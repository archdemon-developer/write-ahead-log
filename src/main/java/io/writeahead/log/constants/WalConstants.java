package io.writeahead.log.constants;

public class WalConstants {
  private WalConstants() {}

  public static final String LOG_FILE_DATE_FORMAT = "YYYY-MM-DD-HHMMSS";
  public static final int SEGMENT_HEADER_SIZE = 48;
  public static final int SEGMENT_FOOTER_SIZE = 36;
}
