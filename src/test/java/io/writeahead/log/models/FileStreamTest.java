package io.writeahead.log.models;

import static org.junit.jupiter.api.Assertions.*;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FileStream Tests — 100% Validation Coverage")
class FileStreamTest {

  private static class TrackableFileOutputStream extends FileOutputStream {
    private boolean closeCalled = false;
    private IOException closeException = null;

    TrackableFileOutputStream(IOException closeException) throws IOException {
      super("/dev/null");
      this.closeException = closeException;
    }

    TrackableFileOutputStream() throws IOException {
      this(null);
    }

    @Override
    public void close() throws IOException {
      closeCalled = true;
      if (closeException != null) {
        throw closeException;
      }
    }

    boolean wasCloseCalled() {
      return closeCalled;
    }
  }

  /**
   * Test double: DataOutputStream that tracks calls and can throw on demand. Does NOT call
   * super.close() to avoid stream closing side effects.
   */
  private static class TrackableDataOutputStream extends DataOutputStream {
    private boolean closeCalled = false;
    private IOException closeException = null;

    TrackableDataOutputStream(FileOutputStream out, IOException closeException) {
      super(out);
      this.closeException = closeException;
    }

    TrackableDataOutputStream(FileOutputStream out) {
      this(out, null);
    }

    @Override
    public void close() throws IOException {
      closeCalled = true;
      if (closeException != null) {
        throw closeException;
      }
    }

    boolean wasCloseCalled() {
      return closeCalled;
    }
  }

  @Nested
  @DisplayName("FileStream Tests — 100% Validation Coverage")
  class FileStreamValidationTest {

    @Nested
    @DisplayName("Compact Constructor Validation")
    class ConstructorValidation {

      @Test
      void rejectsFileOutputStreamNull() throws IOException {
        TrackableDataOutputStream dos =
            new TrackableDataOutputStream(new TrackableFileOutputStream());
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> new FileStream(null, dos));
        assertTrue(ex.getMessage().contains("fileOutputStream cannot be null"));
      }

      @Test
      void rejectsDataOutputStreamNull() throws IOException {
        TrackableFileOutputStream fos = new TrackableFileOutputStream();
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> new FileStream(fos, null));
        assertTrue(ex.getMessage().contains("dataOutputStream cannot be null"));
        fos.close();
      }

      @Test
      void acceptsValidFileStream() throws IOException {
        TrackableFileOutputStream fos = new TrackableFileOutputStream();
        TrackableDataOutputStream dos = new TrackableDataOutputStream(fos);
        FileStream stream = new FileStream(fos, dos);
        assertNotNull(stream);
        stream.closeAll();
      }
    }

    @Nested
    @DisplayName("Method Tests — closeAll()")
    class CloseAllMethod {

      @Test
      void closeAllSuccessfullyClosesStreams() throws IOException {
        TrackableFileOutputStream fos = new TrackableFileOutputStream();
        TrackableDataOutputStream dos = new TrackableDataOutputStream(fos);
        FileStream stream = new FileStream(fos, dos);

        stream.closeAll();

        assertTrue(dos.wasCloseCalled(), "DataOutputStream.close() should be called");
        assertTrue(fos.wasCloseCalled(), "FileOutputStream.close() should be called");
      }

      @Test
      void closeAllHandlesFirstStreamThrowingException() throws IOException {
        IOException dosException = new IOException("DOS error");
        TrackableFileOutputStream fos = new TrackableFileOutputStream();
        TrackableDataOutputStream dos = new TrackableDataOutputStream(fos, dosException);

        FileStream stream = new FileStream(fos, dos);
        IOException ex = assertThrows(IOException.class, () -> stream.closeAll());

        assertEquals(dosException, ex);
        assertTrue(fos.wasCloseCalled(), "FileOutputStream.close() should still be called");
      }

      @Test
      void closeAllHandlesSecondStreamThrowingException() throws IOException {
        IOException fosException = new IOException("FOS error");
        TrackableFileOutputStream fos = new TrackableFileOutputStream(fosException);
        TrackableDataOutputStream dos = new TrackableDataOutputStream(fos);

        FileStream stream = new FileStream(fos, dos);
        IOException ex = assertThrows(IOException.class, () -> stream.closeAll());

        assertEquals(fosException, ex);
        assertTrue(dos.wasCloseCalled(), "DataOutputStream.close() should be called first");
      }

      @Test
      void closeAllHandlesBothStreamsThrowing() throws IOException {
        IOException dosException = new IOException("DOS error");
        IOException fosException = new IOException("FOS error");

        TrackableFileOutputStream fos = new TrackableFileOutputStream(fosException);
        TrackableDataOutputStream dos = new TrackableDataOutputStream(fos, dosException);

        FileStream stream = new FileStream(fos, dos);
        IOException ex = assertThrows(IOException.class, () -> stream.closeAll());

        assertEquals(dosException, ex);
        assertTrue(ex.getSuppressed().length > 0, "FOS exception should be suppressed");
        assertEquals(fosException, ex.getSuppressed()[0]);
      }
    }
  }
}
