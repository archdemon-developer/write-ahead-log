package io.writeahead.log.meta;

import io.writeahead.log.models.WalMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class MetaDataManager {

    private final String metaPath;

    public MetaDataManager(String logDirPath) {
        this.metaPath = logDirPath + "/.meta";
    }

    public WalMetadata read() throws IOException {
        Path metadataPath = Paths.get(metaPath);
        if(!Files.exists(metadataPath)) {
            return new WalMetadata(null, new ArrayList<>());
        }

        String content = new String(Files.readAllBytes(metadataPath));
        return MetadataParser.parseJson(content);
    }

    public void write(WalMetadata metadata) throws IOException {
        String tempPath = metaPath + ".tmp";
        Path temporaryPath = Paths.get(tempPath);
        String metadataAsJson = MetadataParser.toJson(metadata);


        Files.write(temporaryPath, metadataAsJson.getBytes());

        Files.move(temporaryPath, Paths.get(metaPath),
                StandardCopyOption.ATOMIC_MOVE,  StandardCopyOption.REPLACE_EXISTING);
    }
}
