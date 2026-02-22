class BluetoothDevice {
  final String name;
  final String address; // MAC-адрес
  final BluetoothType type;
  final BondState bondState;

  const BluetoothDevice({required this.name, required this.address, required this.type, required this.bondState});

  factory BluetoothDevice.fromMap(Map<dynamic, dynamic> map) {
    return BluetoothDevice(
      name: map['name'] ?? '',
      address: map['address'] ?? '',
      type: BluetoothType.fromInt(map['type'] as int?),
      bondState: BondState.fromInt(map['bondState'] as int?),
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BluetoothDevice && runtimeType == other.runtimeType && address == other.address;

  @override
  int get hashCode => address.hashCode;

  @override
  String toString() {
    return 'BluetoothDevice{name: $name, address: $address, type: $type, bondState: $bondState}';
  }
}

/// Соответствует константам Android [BluetoothDevice.DEVICE_TYPE_*]
enum BluetoothType {
  unknown(0),
  classic(1),
  le(2),
  dual(3);

  final int value;

  const BluetoothType(this.value);

  static BluetoothType fromInt(int? value) {
    return BluetoothType.values.firstWhere((e) => e.value == value, orElse: () => BluetoothType.unknown);
  }
}

/// Соответствует константам Android [BluetoothDevice.BOND_*]
enum BondState {
  none(10),
  bonding(11),
  bonded(12);

  final int value;

  const BondState(this.value);

  static BondState fromInt(int? value) {
    return BondState.values.firstWhere((e) => e.value == value, orElse: () => BondState.none);
  }
}
