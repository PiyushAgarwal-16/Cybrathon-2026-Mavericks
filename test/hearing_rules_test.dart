import 'package:flutter_test/flutter_test.dart';
import 'package:earbud_tracker/hearing_health.dart';

void main() {
  group('HealthRules Tests', () {
    test('High Intensity Rule', () {
      final insight = HealthRules.getDailyInsight(95 * 60, 85); // 95 mins, 85 vol
      expect(insight.title, 'High Intensity');
      expect(insight.isUrgent, true);
    });

    test('Volume Check Rule', () {
      final insight = HealthRules.getDailyInsight(30 * 60, 80); // 30 mins, 80 vol
      expect(insight.title, 'Volume Check');
      expect(insight.isUrgent, true);
    });

    test('Long Session Rule', () {
      final insight = HealthRules.getDailyInsight(200 * 60, 50); // 200 mins, 50 vol
      expect(insight.title, 'Long Session');
      expect(insight.isUrgent, false);
    });

    test('Steady Listening Rule', () {
      final insight = HealthRules.getDailyInsight(70 * 60, 65); // 70 mins, 65 vol
      expect(insight.title, 'Steady Listening');
      expect(insight.isUrgent, false);
    });

    test('Balanced Day Rule (Default)', () {
      final insight = HealthRules.getDailyInsight(30 * 60, 50); // 30 mins, 50 vol
      expect(insight.title, 'Balanced Day');
      expect(insight.isUrgent, false);
    });
  });
}
