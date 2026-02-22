import 'package:external_app_scanner/external_app_scanner.dart';

class ScannerException implements Exception {
  final Code code;
  final String? errorMessage;
  final dynamic errorDetails;

  const ScannerException({required this.code, required this.errorMessage, required this.errorDetails});

  @override
  String toString() => 'ScannerException{code: $code, errorMessage: $errorMessage, errorDetails: $errorDetails}';
}
