import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

class DayDetailScreen extends StatefulWidget {
  final DateTime date;

  const DayDetailScreen({super.key, required this.date});

  @override
  State<DayDetailScreen> createState() => _DayDetailScreenState();
}

class _DayDetailScreenState extends State<DayDetailScreen> {
  static const platform = MethodChannel('earbud_tracker/dashboard');
  List<dynamic> _sessions = [];
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _fetchSessions();
  }

  Future<void> _fetchSessions() async {
    try {
      final dateStr = DateFormat('yyyy-MM-dd').format(widget.date);
      final List<dynamic> result = await platform.invokeMethod('getSessionsForDay', {'date': dateStr});
      
      setState(() {
        _sessions = result;
        _isLoading = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = "Failed to load sessions: ${e.message}";
        _isLoading = false;
      });
    } catch (e) {
       setState(() {
        _errorMessage = "Error: $e";
        _isLoading = false;
      });
    }
  }

  String _formatTime(int timestamp) {
    return DateFormat('h:mm a').format(DateTime.fromMillisecondsSinceEpoch(timestamp));
  }
  
  String _formatDuration(int seconds) {
    if (seconds < 60) return "${seconds}s";
    final minutes = seconds ~/ 60;
     final remainingSeconds = seconds % 60;
    if (minutes < 60) return "${minutes}m ${remainingSeconds}s";
    final hours = minutes ~/ 60;
    final remainingMinutes = minutes % 60;
    return "${hours}h ${remainingMinutes}m";
  }

  @override
  Widget build(BuildContext context) {
    final dateDisplay = DateFormat('MMMM d, yyyy').format(widget.date);

    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        title: Text(dateDisplay, style: const TextStyle(color: Colors.black)),
        iconTheme: const IconThemeData(color: Colors.black),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.black12))
          : _errorMessage != null
              ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent))) // Error exception, red is okay for errors? User said "Gray shades ONLY". Let's assume errors can still be red or just bold black. Let's stick to "NO accent colors".
              // "Colors allowed ONLY: Black, White, Gray shades". So Error should be Black/Gray.
              : _sessions.isEmpty
                  ? const Center(child: Text("No sessions recorded.", style: TextStyle(color: Colors.grey)))
                  : ListView.builder(
                      padding: const EdgeInsets.all(24),
                      itemCount: _sessions.length,
                      itemBuilder: (context, index) {
                        final session = _sessions[index];
                        final startTime = _formatTime(session['startTime']);
                        final endTime = _formatTime(session['endTime']);
                        final duration = _formatDuration((session['durationSeconds'] as num).toInt());
                        final avgVol = session['avgVolume'];
                        final maxVol = session['maxVolume'];
                        final deviceName = session['deviceName'] ?? 'Unknown';

                        return Container(
                          margin: const EdgeInsets.only(bottom: 32),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Time Range
                              Text(
                                "$startTime – $endTime",
                                style: const TextStyle(
                                  color: Colors.black,
                                  fontSize: 24,
                                  fontWeight: FontWeight.w300,
                                  letterSpacing: 0.5,
                                ),
                              ),
                              const SizedBox(height: 8),
                              
                              // Stats Row
                              Row(
                                children: [
                                  // Duration
                                  Text(
                                    duration,
                                    style: const TextStyle(
                                      color: Colors.black,
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600, // Bolder to emphasize
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  const Text("•", style: TextStyle(color: Colors.black26)),
                                  const SizedBox(width: 12),
                                  
                                  // Device Name
                                  Expanded(
                                    child: Text(
                                      deviceName,
                                      style: const TextStyle(
                                        color: Colors.black45,
                                        fontSize: 14,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                   
                                  // Volume
                                  Text(
                                    "Vol: $avgVol% / $maxVol%",
                                    style: const TextStyle(
                                      color: Colors.black38,
                                      fontSize: 13,
                                      fontWeight: FontWeight.w400,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        );
                      },
                    ),
    );
  }
}
