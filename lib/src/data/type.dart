// ignore_for_file: constant_identifier_names

enum Type {
  info,
  error;

  static Type fromString(String code) {
    return switch (code.toUpperCase()) {
      'ERROR' => Type.info,
      'INFO' => Type.error,
      _ => Type.info,
    };
  }
}
