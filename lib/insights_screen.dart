import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:ui' as ui;
import 'package:intl/intl.dart';

class InsightsScreen extends StatefulWidget {
  const InsightsScreen({super.key});

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  static const platform = MethodChannel('earbud_tracker/dashboard');
  List<dynamic> _weeklyData = [];
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _fetchInsights();
  }

  Future<void> _fetchInsights() async {
    try {
      final List<dynamic> result = await platform.invokeMethod('getLast7DaysSummary');
      
      setState(() {
        _weeklyData = result;
        _isLoading = false;
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMessage = "Failed to load insights: ${e.message}";
        _isLoading = false;
      });
    } catch (e) {
       setState(() {
        _errorMessage = "Error: $e";
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        title: const Text("Insights", style: TextStyle(color: Colors.black, letterSpacing: 1.0)),
        iconTheme: const IconThemeData(color: Colors.black),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.black12))
          : _errorMessage != null
              ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold)))
              : _weeklyData.isEmpty
                  ? const Center(child: Text("Not enough data yet.", style: TextStyle(color: Colors.grey)))
                  : SingleChildScrollView(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            "LAST 7 DAYS",
                            style: TextStyle(
                              color: Colors.grey,
                              fontSize: 12,
                              letterSpacing: 2,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(height: 32),
                          
                          // Chart Container
                          SizedBox(
                            height: 250,
                            width: double.infinity,
                            child: CustomPaint(
                              painter: TrendsPainter(_weeklyData),
                            ),
                          ),
                          
                          const SizedBox(height: 48),
                          
                          _buildHighlights(),
                        ],
                      ),
                    ),
    );
  }

  Widget _buildHighlights() {
    if (_weeklyData.isEmpty) return const SizedBox.shrink();

    // Find max duration day
    final maxDurationDay = _weeklyData.reduce((curr, next) => 
      (curr['totalDurationSeconds'] as num) > (next['totalDurationSeconds'] as num) ? curr : next);
      
    // Find max avg volume day
    final maxVolDay = _weeklyData.reduce((curr, next) => 
      (curr['avgVolume'] as num) > (next['avgVolume'] as num) ? curr : next);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildHighlightItem(
          "MOST ACTIVE DAY",
          _formatDate(maxDurationDay['date']),
          _formatDuration((maxDurationDay['totalDurationSeconds'] as num).toInt()),
        ),
        const SizedBox(height: 32),
        _buildHighlightItem(
          "HIGHEST AVG VOLUME",
          _formatDate(maxVolDay['date']),
          "${maxVolDay['avgVolume']}%",
        ),
      ],
    );
  }

  Widget _buildHighlightItem(String label, String date, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Colors.grey,
            fontSize: 12,
            letterSpacing: 1.5,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              date,
              style: const TextStyle(color: Colors.black, fontSize: 16),
            ),
            Text(
              value,
              style: const TextStyle(
                color: Colors.black,
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        const Divider(color: Colors.black12),
      ],
    );
  }

  String _formatDate(String dateStr) {
     try {
      final date = DateFormat('yyyy-MM-dd').parse(dateStr);
      return DateFormat('EEEE').format(date); // Full Day Name
    } catch (e) {
      return dateStr;
    }
  }

  String _formatDuration(int seconds) {
    if (seconds == 0) return "0m";
    final hours = seconds ~/ 3600;
    final minutes = (seconds % 3600) ~/ 60;
    if (hours > 0) return "${hours}h ${minutes}m";
    return "${minutes}m";
  }
}

class TrendsPainter extends CustomPainter {
  final List<dynamic> data;

  TrendsPainter(this.data);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..strokeCap = StrokeCap.round;
    final textPainter = TextPainter(textDirection: ui.TextDirection.ltr);

    // Calc max values for scaling
    double maxDuration = 0;
    for (var d in data) {
      final dur = (d['totalDurationSeconds'] as num).toDouble();
      if (dur > maxDuration) maxDuration = dur;
    }
    if (maxDuration == 0) maxDuration = 1; // avoid div by zero

    final barWidth = (size.width / data.length) * 0.4;
    final spacing = (size.width / data.length);

    for (int i = 0; i < data.length; i++) {
        final item = data[i];
        final duration = (item['totalDurationSeconds'] as num).toDouble();
        
        // Draw Bar (Duration)
        final barHeight = (duration / maxDuration) * (size.height - 30); // leave 30px for text
        final x = (i * spacing) + (spacing / 2);
        final y = size.height - 30; // bottom baseline for bars

        // Actual Bar
        paint.color = Colors.black; // Solid black or high opacity
        canvas.drawRRect(
            RRect.fromRectAndRadius(
                Rect.fromLTWH(x - barWidth / 2, y - barHeight, barWidth, barHeight),
                const Radius.circular(4)
            ),
            paint
        );

        // Draw Volume Dot (Avg Volume) - scaled 0-100% to chart height
        final avgVol = (item['avgVolume'] as num).toDouble();
        final volY = size.height - 30 - ((avgVol / 100.0) * (size.height - 30));
        
        paint.color = Colors.grey;
        paint.style = PaintingStyle.fill;
        canvas.drawCircle(Offset(x, volY), 3, paint);

        // Draw Day Label
        try {
            final date = DateFormat('yyyy-MM-dd').parse(item['date']);
            final dayLetter = DateFormat('E').format(date)[0]; // First letter
            
            textPainter.text = TextSpan(
                text: dayLetter,
                style: const TextStyle(color: Colors.black54, fontSize: 12),
            );
            textPainter.layout();
            textPainter.paint(canvas, Offset(x - textPainter.width / 2, size.height - 15));
        } catch (_) {}
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
