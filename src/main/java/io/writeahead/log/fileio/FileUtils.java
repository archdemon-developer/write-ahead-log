package io.writeahead.log.fileio;

import io.writeahead.log.models.FileStream;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileUtils {

    private FileUtils(){}

    public static FileStream openAppendStream(File file) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(file, true);
        DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
        return new FileStream(fileOutputStream, dataOutputStream);
    }

    public static void writeToStream(FileStream fileStream, byte[] data) throws IOException {
        DataOutputStream dataOutputStream = fileStream.getDataOutputStream();
        dataOutputStream.write(data);
    }

    public static void fsyncStream(FileStream fileStream) throws IOException {
        FileOutputStream fileOutputStream = fileStream.getFileOutputStream();
        fileOutputStream.getFD().sync();
    }

    public static void closeStream(FileStream fileStream) throws IOException {
        DataOutputStream dataOutputStream = fileStream.getDataOutputStream();
        dataOutputStream.close();
    }

    public static byte[] readAllBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    public static long getFileSize(File file) {
        return file.length();
    }

    public static boolean deleteFile(File file) throws IOException {
        return Files.deleteIfExists(file.toPath());
    }

    public static boolean fileExists(File file) {
        return file.exists();
    }

    public static File getLogFile(String directory, String filename) {
        return new File(directory + "/" + filename);
    }

    public static void createDirectory(String directoryPath) throws IOException {
        Path directory = Paths.get(directoryPath);
        if(!Files.exists(directory)){
            Files.createDirectories(directory);
        }
    }

    public static List<File> listLogFiles(String directory) {
        File dirToList = new File(directory);
        if(dirToList.exists() && dirToList.isDirectory()) {
            File[] files = dirToList.listFiles();

            if(files == null) {
                return  new ArrayList<>();
            }

            List<File> logFiles = new  ArrayList<>();

            Arrays.stream(files)
                    .filter(file ->
                            getFileExtension(file).equalsIgnoreCase("log"))
                    .forEach(logFiles::add);

            logFiles.sort(Comparator.comparing(File::getName));

            return logFiles;
        }

        return new ArrayList<>();
    }

    private static String getFileExtension(File file) {
        String filename = file.getName();
        int fileExtensionPosition =  filename.lastIndexOf('.');
        if(fileExtensionPosition == -1) {
            return "";
        }
        return filename.substring(fileExtensionPosition + 1);
    }
}
