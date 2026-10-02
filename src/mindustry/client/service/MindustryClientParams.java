package mindustry.client.service;

import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.services.*;

/** Parameters passed to {@link MindustryClientService}. */
public interface MindustryClientParams extends BuildServiceParameters{
    /** @return Whether the Steam installation should be ignored; defaults to {@code mindustryIgnoreSteam} property. */
    Property<Boolean> getIgnoreSteam();

    /** @return The explicitly-provided Mindustry path, if set; defaults to {@code mindustryPath} property if found. */
    RegularFileProperty getPath();
}