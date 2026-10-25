package org.fuin.porelo.machines;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.fuin.porelo.json.Json;
import org.fuin.porelo.json.JsonException;

/**
 * The host's local VM pool, as porelo-host's role {@code vm_pool} writes it.
 *
 * <p><strong>A contract with another repository, read strictly.</strong> The file is the one thing
 * porelo-host and this module share about the pool, so a format this does not know, a field that is
 * missing or an account named twice stops the run here, saying which, instead of renting an account
 * that is not there.
 *
 * <p><strong>Nothing secret in the file.</strong> The run account's key comes from the environment
 * or {@code --key}, and an account is made clean by giving itself back, so nothing here reaches the
 * host or another VM: the builds that rent from the pool include task branches nobody reviewed.
 *
 * @param vms The pool's VMs.
 */
public record Pool(List<Vm> vms) {

    /** The format this reads, and the only one. */
    public static final String FORMAT = "porelo-machines-pool/1";

    /**
     * One VM of the pool.
     *
     * @param name Its libvirt domain, which also names its locks.
     * @param os What a {@link Spec} asks for to get it, such as {@code ubuntu}.
     * @param address Where it is.
     * @param hostKey Its host key, an OpenSSH public key line.
     * @param accounts Its run accounts.
     */
    public record Vm(
            String name, String os, String address, String hostKey, List<String> accounts) {

        /**
         * Compact constructor, keeping the accounts as given.
         *
         * @param name Its libvirt domain.
         * @param os What a spec asks for.
         * @param address Where it is.
         * @param hostKey Its host key.
         * @param accounts Its run accounts.
         */
        public Vm {
            accounts = List.copyOf(accounts);
        }
    }

    /**
     * Compact constructor, keeping the VMs as given.
     *
     * @param vms The pool's VMs.
     */
    public Pool {
        vms = List.copyOf(vms);
    }

    /**
     * Reads a pool file.
     *
     * @param file The file porelo-host wrote.
     * @return The pool.
     * @throws IOException If it cannot be read, or is not a pool this module knows.
     */
    public static Pool read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("No pool file at " + file + ": porelo-host's up writes it.");
        }
        final Map<String, Object> body;
        try {
            body = Values.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (JsonException | IllegalArgumentException ex) {
            throw new IOException(
                    "The pool file " + file + " is no JSON object: " + ex.getMessage(), ex);
        }
        final String format = Values.text(body, "format");
        if (!FORMAT.equals(format)) {
            throw new IOException(
                    "The pool file "
                            + file
                            + " is of format '"
                            + format
                            + "'; this reads "
                            + FORMAT
                            + " only.");
        }
        final List<Vm> vms = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        for (final Map<String, Object> each : Values.objects(body, "vms")) {
            final String name = required(file, each, "name");
            if (!names.add(name)) {
                throw new IOException(
                        "The pool file " + file + " names the VM '" + name + "' twice.");
            }
            final List<String> accounts = new ArrayList<>();
            if (each.get("accounts") instanceof List<?> listed) {
                for (final Object account : listed) {
                    final String text = account instanceof String s ? s : "";
                    if (!text.matches("[a-z_][a-z0-9_-]{0,31}")) {
                        throw new IOException(
                                "The pool file "
                                        + file
                                        + " names an account '"
                                        + account
                                        + "' on "
                                        + name
                                        + " that is no account name.");
                    }
                    if (accounts.contains(text)) {
                        throw new IOException(
                                "The pool file "
                                        + file
                                        + " names the account '"
                                        + text
                                        + "' twice on "
                                        + name
                                        + ".");
                    }
                    accounts.add(text);
                }
            }
            if (accounts.isEmpty()) {
                throw new IOException(
                        "The pool file " + file + " gives the VM '" + name + "' no accounts.");
            }
            vms.add(
                    new Vm(
                            name,
                            required(file, each, "os"),
                            required(file, each, "address"),
                            required(file, each, "hostKey"),
                            accounts));
        }
        if (body.containsKey("runKey")) {
            throw new IOException(
                    "The pool file "
                            + file
                            + " still carries 'runKey': the run key comes from "
                            + Main.POOL_SSH
                            + " or --key, never from the pool file.");
        }
        if (vms.isEmpty()) {
            throw new IOException("The pool file " + file + " names no VMs.");
        }
        return new Pool(vms);
    }

    /**
     * Returns how many run accounts the pool has for a system.
     *
     * @param os What a spec asks for.
     * @return The number, {@code 0} when no VM is of that system.
     */
    public int size(String os) {
        return vms.stream()
                .filter(vm -> vm.os().equals(os))
                .mapToInt(vm -> vm.accounts().size())
                .sum();
    }

    private static String required(Path file, Map<String, Object> body, String field)
            throws IOException {
        final String value = Values.text(body, field);
        if (value.isBlank()) {
            throw new IOException("The pool file " + file + " has no '" + field + "'.");
        }
        return value;
    }
}
