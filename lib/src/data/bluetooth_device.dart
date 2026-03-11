class ExtBluetoothDevice {
  final String name;
  final String address; // MAC-адрес
  final ExtBluetoothType type;
  final ExtBondState bondState;

  const ExtBluetoothDevice({required this.name, required this.address, required this.type, required this.bondState});

  factory ExtBluetoothDevice.fromMap(Map<dynamic, dynamic> map) {
    return ExtBluetoothDevice(
      name: map['name'] ?? '',
      address: map['address'] ?? '',
      type: ExtBluetoothType.fromInt(map['type'] as int?),
      bondState: ExtBondState.fromInt(map['bondState'] as int?),
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ExtBluetoothDevice && runtimeType == other.runtimeType && address == other.address;

  @override
  int get hashCode => address.hashCode;

  @override
  String toString() {
    return 'BluetoothDevice{name: $name, address: $address, type: $type, bondState: $bondState}';
  }
}

/// Соответствует константам Android [BluetoothDevice.DEVICE_TYPE_*]
enum ExtBluetoothType {
  unknown(0),
  classic(1),
  le(2),
  dual(3);

  final int value;

  const ExtBluetoothType(this.value);

  static ExtBluetoothType fromInt(int? value) {
    return ExtBluetoothType.values.firstWhere((e) => e.value == value, orElse: () => ExtBluetoothType.unknown);
  }
}

/// Соответствует константам Android [BluetoothDevice.BOND_*]
enum ExtBondState {
  none(10),
  bonding(11),
  bonded(12);

  final int value;

  const ExtBondState(this.value);

  static ExtBondState fromInt(int? value) {
    return ExtBondState.values.firstWhere((e) => e.value == value, orElse: () => ExtBondState.none);
  }
}
