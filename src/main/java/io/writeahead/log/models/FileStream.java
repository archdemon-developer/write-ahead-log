package io.writeahead.log.models;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public record FileStream(FileOutputStream fileOutputStream, DataOutputStream dataOutputStream) {
  public FileStream {
    if (fileOutputStream == null) {
      throw new IllegalArgumentException("fileOutputStream cannot be null");
    }
    if (dataOutputStream == null) {
      throw new IllegalArgumentException("dataOutputStream cannot be null");
    }
  }

  public void closeAll() throws IOException {
    IOException firstException = null;

    try {
      dataOutputStream.close();
    } catch (IOException ex) {
      firstException = ex;
    }

    try {
      fileOutputStream.close();
    } catch (IOException ex) {
      if (firstException != null) {
        firstException.addSuppressed(ex);
        throw firstException;
      }
      throw ex;
    }

    if (firstException != null) {
      throw firstException;
    }
  }
}
