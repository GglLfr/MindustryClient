package mindustry.client;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Client information that has been detected.
 * @param ignoreSteam   Whether the Steam installation should be ignored. If {@link #path} isn't {@code null} and points
 *                      to a Steam installation, the Steam one will be used anyway.
 * @param steamPath     {@code [...]/steamapps/common/Mindustry} directory, or {@code null} if not found.
 * @param path          {@code Mindustry[.exe|.app]} executable path, or {@code null} if not provided.
 * @param dataDirectory The data directory of Mindustry, including the mods folder. Never {@code null}.
 */
public record ClientInfo(
    boolean ignoreSteam, File steamPath, File path, File dataDirectory) implements Serializable{
    /** @return The mods directory; this is either the app-data directory or the Steam {@code saves} directory. */
    public File getModsDirectory(){
        return new File(dataDirectory, "mods");
    }

    /** @return Whether the detected client is a Steam library installation. */
    public boolean isSteam(){
        return steamPath != null && (!ignoreSteam || path != null && path.toPath().toAbsolutePath().startsWith(steamPath.toPath().toAbsolutePath()));
    }

    /**
     * @param ignoreSteam          Whether the Steam installation should be ignored. If {@link #path} isn't {@code null}
     *                             and points to a Steam installation, the Steam one will be used anyway.
     * @param path                 {@code Mindustry[.exe|.app]} executable path, or {@code null} if not provided.
     * @param defaultDataDirectory The data directory of Mindustry, including the mods folder. Never {@code null}.
     * @param os                   The running operating system.
     * @param logger               Logging utility.
     * @return The detected client installation.
     */
    public static ClientInfo detect(boolean ignoreSteam, File path, File defaultDataDirectory, Os os, Logger logger){
        ArrayList<File> steamDirs = new ArrayList<>();
        var home = System.getProperty("user.home");
        if(home == null) home = "./";

        var out = new ClientInfo(ignoreSteam, null, null, defaultDataDirectory);
        switch(os){
            case windows -> {
                steamDirs.add(new File("/Program Files (x86)/Steam"));
                steamDirs.add(new File("/Program Files/Steam"));

                var env = System.getenv("PROGRAMFILES(X86)");
                if(env != null) steamDirs.add(new File(env, "Steam"));

                env = System.getenv("PROGRAMFILES");
                steamDirs.add(new File(env, "Steam"));
            }
            case macOS -> steamDirs.add(new File(home, "Library/Application Support/Steam"));
            case linux -> {
                steamDirs.add(new File(home, ".local/share/Steam"));
                steamDirs.add(new File(home, ".steam/steam"));
                steamDirs.add(new File(home, ".var/app/com.valvesoftware.Steam/.local/share/Steam"));
            }
        }

        var steamRoot = steamDirs.stream().findFirst();
        if(steamRoot.isPresent()){
            var libraryPaths = new ArrayList<>(Collections.singletonList(new File(steamRoot.get(), "steamapps")));
            var vdfFile = new File(steamRoot.get(), "steamapps/libraryfolders.vdf");
            if(vdfFile.exists()){
                try{
                    var m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(Files.readString(vdfFile.toPath(), StandardCharsets.UTF_8));
                    while(m.find()){
                        var dir = new File(m.group(1).replace("\\\\", "\\"));
                        if(dir.exists()) libraryPaths.add(new File(dir, "steamapps"));
                    }
                }catch(IOException ignored){
                }
            }

            var steamPath = libraryPaths.stream().flatMap(steamapps -> {
                var acf = new File(steamapps, "appmanifest_1127400.acf");
                if(!acf.exists()) return Stream.empty();

                String name;
                try{
                    var m = Pattern.compile("\"installdir\"\\s+\"([^\"]+)\"").matcher(Files.readString(vdfFile.toPath(), StandardCharsets.UTF_8));
                    if(!m.find()) throw new IOException();
                    name = m.group(1);
                }catch(IOException ignored){
                    return Stream.empty();
                }

                var dir = new File(steamapps, name);
                return Stream.ofNullable(dir.exists() ? dir : null);
            }).findFirst();

            if(steamPath.isPresent()){
                if(!ignoreSteam)
                    logger.lifecycle(String.format("Found a Steam Mindustry installation at `%s`.", steamPath.get()));

                out = new ClientInfo(ignoreSteam, steamPath.get(), null, new File(steamPath.get(), "saves"));
            }
        }

        if(path != null && (out.steamPath == null || ignoreSteam)){
            if(path.exists()){
                if(os == Os.macOS) path = new File(path, "Contents/MacOS/Mindustry");
                logger.lifecycle(String.format("Using explicitly provided Mindustry executable at `%s`.", path));

                out = new ClientInfo(ignoreSteam, out.steamPath, path, out.dataDirectory);
            }else{
                logger.warn(String.format("Provided Mindustry executable path `%s` does not exist.", path));
            }
        }

        if(!out.isSteam()) out = new ClientInfo(ignoreSteam, out.steamPath, out.path, defaultDataDirectory);
        return out;
    }

    /** Operating system enum, used as a thin shim to not depend on Mindustry's classpath directly. */
    public enum Os{
        /** Windows. */
        windows,
        /** MacOSX/ */
        macOS,
        /** Linux. */
        linux
    }

    /** Simple logging facility. */
    public interface Logger{
        /**
         * Equivalent to Gradle's lifecycle logging.
         * @param log The string to log.
         */
        void lifecycle(String log);

        /**
         * Equivalent to Gradle's warn logging.
         * @param log The string to log.
         */
        void warn(String log);
    }
}
