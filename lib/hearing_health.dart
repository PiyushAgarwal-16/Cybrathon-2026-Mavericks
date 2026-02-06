class HearingInsight {
  final String title;
  final String message;
  final bool isUrgent;

  HearingInsight({
    required this.title,
    required this.message,
    this.isUrgent = false,
  });
}

class HealthRules {
  static HearingInsight getDailyInsight(int totalSeconds, int avgVol) {
    // Convert seconds to minutes for easier rules
    final int minutes = totalSeconds ~/ 60;

    // Rule 1: High Volume + Long Duration (Most Critical)
    if (avgVol > 80 && minutes > 90) {
      return HearingInsight(
        title: "High Intensity",
        message: "Listening loud for this long can be tiring for your ears. Consider a break.",
        isUrgent: true,
      );
    }

    // Rule 2: High Volume
    if (avgVol > 75) {
      return HearingInsight(
        title: "Volume Check",
        message: "Volume levels are trending high today. Lowering it slightly helps long-term health.",
        isUrgent: true,
      );
    }

    // Rule 3: Very Long Duration
    if (minutes > 180) {
      return HearingInsight(
        title: "Long Session",
        message: "You've been plugged in for a while. Your ears might enjoy a quiet break.",
        isUrgent: false,
      );
    }

    // Rule 4: Moderate Usage
    if (minutes > 60 && avgVol > 60) {
      return HearingInsight(
        title: "Steady Listening",
        message: "You're getting good use of your earbuds. Keeping volume moderate is key.",
        isUrgent: false,
      );
    }

    // Default / Low Usage
    return HearingInsight(
      title: "Balanced Day",
      message: "Your listening levels are within a comfortable range today.",
      isUrgent: false,
    );
  }
}
