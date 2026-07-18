package io.writeahead.log.models;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

public class FileStream {

    private FileOutputStream fileOutputStream;
    private DataOutputStream dataOutputStream;

    public FileStream(FileOutputStream fileOutputStream, DataOutputStream dataOutputStream) {
        this.fileOutputStream = fileOutputStream;
        this.dataOutputStream = dataOutputStream;
    }

    public FileOutputStream getFileOutputStream() {
        return fileOutputStream;
    }

    public DataOutputStream getDataOutputStream() {
        return dataOutputStream;
    }
}
