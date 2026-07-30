package io.writeahead.log.models;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

public record FileStream(FileOutputStream fileOutputStream, DataOutputStream dataOutputStream) {}
