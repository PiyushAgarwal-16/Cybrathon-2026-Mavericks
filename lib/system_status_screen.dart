import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class SystemStatusScreen extends StatefulWidget {
  const SystemStatusScreen({super.key});

  @override
  State<SystemStatusScreen> createState() => _SystemStatusScreenState();
}

class _SystemStatusScreenState extends State<SystemStatusScreen> with WidgetsBindingObserver {
  static const platform = MethodChannel('earbud_tracker/dashboard');
  
  bool _isLoading = true;
  Map<dynamic, dynamic> _status = {};
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkStatus();
    }
  }

  Future<void> _checkStatus() async {
    try {
      final Map<dynamic, dynamic> result = await platform.invokeMethod('getSystemStatus');
      if (mounted) {
        setState(() {
          _status = result;
          _isLoading = false;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = "Failed to load status: ${e.message}";
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _invokeAction(String method) async {
    try {
      await platform.invokeMethod(method);
      // Status will update on resume
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          "SYSTEM STATUS",
          style: TextStyle(
            color: Colors.black,
            fontSize: 14,
            fontWeight: FontWeight.bold,
            letterSpacing: 1.5,
          ),
        ),
        centerTitle: true,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.black12))
          : _errorMessage != null
              ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.black)))
              : ListView(
                  padding: const EdgeInsets.all(24),
                  children: [
                    _buildStatusItem(
                      title: "Notifications",
                      isOk: _status['notificationsEnabled'] == true,
                      failMessage: "Notifications blocked",
                      okMessage: "Enabled",
                      actionLabel: "SETTINGS",
                      onAction: () => _invokeAction('openNotificationSettings'),
                    ),
                    const Divider(height: 32),
                     _buildStatusItem(
                      title: "Battery Optimization",
                      isOk: _status['batteryOptimizationsIgnored'] == true,
                      failMessage: "Restricted (May stop tracking)",
                      okMessage: "Unrestricted",
                      actionLabel: "FIX",
                      onAction: () => _invokeAction('openBatterySettings'),
                    ),
                    const Divider(height: 32),
                     _buildStatusItem(
                      title: "Bluetooth",
                      isOk: _status['bluetoothPermissionGranted'] == true && _status['bluetoothEnabled'] == true,
                      failMessage: _status['bluetoothPermissionGranted'] == false 
                          ? "Permission Missing" 
                          : "Bluetooth is OFF",
                      okMessage: "Ready",
                      actionLabel: _status['bluetoothPermissionGranted'] == false ? "ALLOW" : "TURN ON",
                      onAction: () => _invokeAction(
                          _status['bluetoothPermissionGranted'] == false 
                            ? 'openAppSettings' 
                            : 'openBluetoothSettings'
                      ),
                    ),
                    const Divider(height: 32),
                     _buildStatusItem(
                      title: "Tracking Service",
                      isOk: _status['serviceAlive'] == true,
                      failMessage: "Service Stopped",
                      okMessage: "Running",
                      actionLabel: "RESTART",
                      onAction: () {
                         _invokeAction('restartService');
                         Future.delayed(const Duration(seconds: 1), _checkStatus);
                      }
                    ),
                    const Divider(height: 32),
                     _buildStatusItem(
                      title: "Audio Monitor",
                      isOk: true, // Informational
                      failMessage: "",
                      okMessage: _status['audioActive'] == true ? "Active (Audio playing)" : "Idle",
                      actionLabel: null,
                      onAction: () {},
                      isInfo: true
                    ),
                  ],
                ),
    );
  }

  Widget _buildStatusItem({
    required String title,
    required bool isOk,
    required String failMessage,
    required String okMessage,
    String? actionLabel,
    required VoidCallback onAction,
    bool isInfo = false,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Status Dot
        Container(
          margin: const EdgeInsets.only(top: 4),
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: isOk 
                ? (isInfo ? Colors.grey : Colors.black) 
                : Colors.transparent,
            border: Border.all(
              color: isOk ? (isInfo ? Colors.grey : Colors.black) : Colors.black,
              width: 2
            ),
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 16),
        
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title.toUpperCase(),
                style: const TextStyle(
                  color: Colors.grey,
                  fontSize: 12,
                  letterSpacing: 1.0,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                isOk ? okMessage : failMessage,
                style: TextStyle(
                  color: Colors.black,
                  fontSize: 16,
                  fontWeight: isOk ? FontWeight.w500 : FontWeight.bold,
                ),
              ),
            ],
          ),
        ),
        
        if (!isOk && actionLabel != null)
          TextButton(
            onPressed: onAction,
            style: TextButton.styleFrom(
              foregroundColor: Colors.black,
              side: const BorderSide(color: Colors.black),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            ),
            child: Text(
              actionLabel,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 12,
                letterSpacing: 0.5
              ),
            ),
          )
      ],
    );
  }
}
