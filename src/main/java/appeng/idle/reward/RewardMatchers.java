package appeng.idle.reward;

import java.util.EnumMap;
import java.util.Map;

final class RewardMatchers {
    private static final Map<RewardTriggerType, RewardMatcher> MATCHERS = createMatchers();

    private RewardMatchers() {
    }

    static RewardMatcher forTrigger(RewardTriggerType triggerType) {
        var matcher = MATCHERS.get(triggerType);
        if (matcher == null) {
            throw new IllegalStateException("No reward matcher registered for trigger type " + triggerType);
        }
        return matcher;
    }

    private static Map<RewardTriggerType, RewardMatcher> createMatchers() {
        var map = new EnumMap<RewardTriggerType, RewardMatcher>(RewardTriggerType.class);
        register(map, new BlockBreakRewardMatcher());
        return Map.copyOf(map);
    }

    private static void register(Map<RewardTriggerType, RewardMatcher> map, RewardMatcher matcher) {
        map.put(matcher.triggerType(), matcher);
    }
}
