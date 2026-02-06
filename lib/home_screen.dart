import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'day_detail_screen.dart';
import 'history_screen.dart';
import 'insights_screen.dart';
import 'login_screen.dart';
import 'hearing_health.dart';
import 'insight_card.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const platform = MethodChannel('earbud_tracker/dashboard');
  
  String totalTime = "--";
  String avgVolume = "--";
  String maxVolume = "--";
  String sessionCount = "--";
  HearingInsight? dailyInsight;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    try {
      final Map<dynamic, dynamic> result = await platform.invokeMethod('getTodaySummary');
      
      final int seconds = (result['totalDurationSeconds'] as num?)?.toInt() ?? 0;
      final int avgVol = (result['avgVolume'] as num?)?.toInt() ?? 0;
      final int maxVol = (result['maxVolume'] as num?)?.toInt() ?? 0;
      final int count = (result['sessionCount'] as num?)?.toInt() ?? 0;

      setState(() {
        totalTime = _formatDuration(seconds);
        avgVolume = avgVol.toString();
        maxVolume = maxVol.toString();
        sessionCount = count.toString();
        
        dailyInsight = HealthRules.getDailyInsight(seconds, avgVol);
      });
    } on PlatformException catch (e) {
      print("Failed to get data: '${e.message}'.");
    }
  }

  String _formatDuration(int seconds) {
    int h = seconds ~/ 3600;
    int m = (seconds % 3600) ~/ 60;
    if (h > 0) return "${h}h ${m}m";
    return "${m}m";
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        actions: [
          StreamBuilder<User?>(
            stream: FirebaseAuth.instance.authStateChanges(),
            builder: (context, snapshot) {
              final isLoggedIn = snapshot.hasData;
              return IconButton(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) => const LoginScreen()),
                  );
                },
                icon: Icon(
                  isLoggedIn ? Icons.account_circle : Icons.login,
                  color: isLoggedIn ? Colors.black : Colors.black45,
                ),
              );
            },
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                const SizedBox(height: 32),
                const Text(
                  "Today",
                  style: TextStyle(
                    fontSize: 16, 
                    fontWeight: FontWeight.w500,
                    color: Colors.grey,
                    letterSpacing: 1.2
                  ),
                ),
                const SizedBox(height: 16),
                // Big Timer Text
                Text(
                  totalTime,
                  style: const TextStyle(
                    fontSize: 72,
                    fontWeight: FontWeight.w300, 
                    color: Colors.black,
                    height: 1.0,
                  ),
                ),
                const SizedBox(height: 48),
                
                // Stats Row
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                     _buildStatItem("Avg Vol", avgVolume),
                     _buildDivider(),
                     _buildStatItem("Max Vol", maxVolume),
                     _buildDivider(),
                     _buildStatItem("Sessions", sessionCount),
                  ],
                ),

                if (dailyInsight != null) ...[
                  const SizedBox(height: 48),
                  InsightCard(insight: dailyInsight!),
                ],
                
                const SizedBox(height: 48),
                
                // View Sessions Button
                OutlinedButton(
                  onPressed: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => DayDetailScreen(date: DateTime.now()),
                      ),
                    );
                    _fetchData();
                  },
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.black,
                    side: const BorderSide(color: Colors.grey),
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(30),
                    ),
                  ),
                  child: const Text("View today's sessions"),
                ),
                
                const SizedBox(height: 24),

                // Bottom Links
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    TextButton(
                      onPressed: () {
                         Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => const HistoryScreen(),
                          ),
                        );
                      },
                      child: const Text(
                        "HISTORY",
                        style: TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                          letterSpacing: 1.5,
                        ),
                      ),
                    ),
                    const SizedBox(width: 24),
                    TextButton(
                      onPressed: () {
                         Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => const InsightsScreen(),
                          ),
                        );
                      },
                      child: const Text(
                        "INSIGHTS",
                        style: TextStyle(
                          color: Colors.grey,
                          fontSize: 12,
                          letterSpacing: 1.5,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 32),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStatItem(String label, String value) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: Colors.black87,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            color: Colors.grey,
            fontWeight: FontWeight.w500
          ),
        ),
      ],
    );
  }

  Widget _buildDivider() {
    return Container(
      height: 32,
      width: 1,
      color: Colors.grey.shade300,
    );
  }
}
