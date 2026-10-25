package com.aitravel.session.profile;

import com.aitravel.session.model.Interest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

/**
 * Profiles in the sessions table ({@code travel.session.store=dynamodb} with Postgres off), one item per traveler
 * under {@code profile#<owner hash>}. Like plan items they have no {@code status}, so the session capacity scan never
 * counts them. Each visit pushes {@code purgeAt} out by {@link #RETENTION}, so a traveler who has not been back for a
 * year is deleted by the table's TTL.
 */
public class DynamoDbProfileStore implements ProfileStore {

    static final String KEY = "sessionId";
    static final String PREFIX = "profile#";
    static final String DISPLAY_NAME = "displayName";
    static final String HOME_CITY = "homeCity";
    static final String INTERESTS = "interests";
    static final String CURRENCY = "currency";
    static final String TRAVEL_MODE = "travelMode";
    static final String CREATED_AT = "profileCreatedAt";
    static final String UPDATED_AT = "profileUpdatedAt";
    static final String LAST_SEEN_AT = "lastSeenAt";
    static final String PURGE_AT = "purgeAt";
    static final Duration RETENTION = Duration.ofDays(365);

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbProfileStore(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public TravelerProfile touch(String ownerKey, Instant now) {
        Map<String, AttributeValue> values = new HashMap<>(timestamps(now));
        values.put(":walk", AttributeValue.fromS(TravelerProfile.DEFAULT_TRAVEL_MODE));
        values.put(":none", AttributeValue.fromL(List.of()));
        return update(ownerKey, "SET " + String.join(", ",
                "#travelMode = if_not_exists(#travelMode, :walk)",
                "#interests = if_not_exists(#interests, :none)",
                "#created = if_not_exists(#created, :now)",
                "#updated = if_not_exists(#updated, :now)",
                "#seen = :now", "#purge = :purge"), values);
    }

    @Override
    public TravelerProfile save(String ownerKey, ProfileFields fields, Instant now) {
        Map<String, AttributeValue> values = new HashMap<>(timestamps(now));
        List<String> set = new ArrayList<>(List.of("#created = if_not_exists(#created, :now)", "#updated = :now",
                "#seen = :now", "#purge = :purge", "#interests = :interests"));
        List<String> remove = new ArrayList<>();
        values.put(":interests", AttributeValue.fromL(fields.interests().stream().map(Interest::name).sorted()
                .map(AttributeValue::fromS).toList()));
        optional("#displayName", ":displayName", fields.displayName(), set, remove, values);
        optional("#homeCity", ":homeCity", fields.homeCity(), set, remove, values);
        optional("#currency", ":currency", fields.currency(), set, remove, values);
        optional("#travelMode", ":travelMode", fields.travelMode() == null
                ? TravelerProfile.DEFAULT_TRAVEL_MODE : fields.travelMode(), set, remove, values);
        String expression = "SET " + String.join(", ", set)
                + (remove.isEmpty() ? "" : " REMOVE " + String.join(", ", remove));
        return update(ownerKey, expression, values);
    }

    private TravelerProfile update(String ownerKey, String expression, Map<String, AttributeValue> values) {
        Map<String, String> names = new HashMap<>();
        names.put("#displayName", DISPLAY_NAME);
        names.put("#homeCity", HOME_CITY);
        names.put("#currency", CURRENCY);
        names.put("#travelMode", TRAVEL_MODE);
        names.put("#interests", INTERESTS);
        names.put("#created", CREATED_AT);
        names.put("#updated", UPDATED_AT);
        names.put("#seen", LAST_SEEN_AT);
        names.put("#purge", PURGE_AT);
        // DynamoDB rejects names the expression does not use.
        names.keySet().removeIf(name -> !expression.contains(name));
        return run(() -> profile(dynamoDb.updateItem(r -> r.tableName(tableName)
                .key(Map.of(KEY, AttributeValue.fromS(PREFIX + ownerKey)))
                .updateExpression(expression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW)).attributes()));
    }

    private static Map<String, AttributeValue> timestamps(Instant now) {
        return Map.of(
                ":now", AttributeValue.fromN(Long.toString(now.toEpochMilli())),
                ":purge", AttributeValue.fromN(Long.toString(now.plus(RETENTION).getEpochSecond())));
    }

    /** Sets the attribute when there is a value and removes it when there is none. */
    private static void optional(String name, String placeholder, String value, List<String> set, List<String> remove,
                                 Map<String, AttributeValue> values) {
        if (value == null) {
            remove.add(name);
        } else {
            set.add(name + " = " + placeholder);
            values.put(placeholder, AttributeValue.fromS(value));
        }
    }

    private static <T> T run(Supplier<T> call) {
        try {
            return call.get();
        } catch (SdkException ex) {
            throw new ProfileUnavailableException(ex);
        }
    }

    private static TravelerProfile profile(Map<String, AttributeValue> item) {
        return new TravelerProfile(
                text(item, DISPLAY_NAME),
                text(item, HOME_CITY),
                interests(item.get(INTERESTS)),
                text(item, CURRENCY),
                text(item, TRAVEL_MODE),
                Instant.ofEpochMilli(Long.parseLong(item.get(CREATED_AT).n())),
                Instant.ofEpochMilli(Long.parseLong(item.get(UPDATED_AT).n())));
    }

    private static String text(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.s();
    }

    /** Names no longer in {@link Interest} are dropped rather than failing the whole profile. */
    private static Set<Interest> interests(AttributeValue list) {
        Set<Interest> interests = EnumSet.noneOf(Interest.class);
        if (list == null || !list.hasL()) {
            return interests;
        }
        list.l().stream()
                .map(AttributeValue::s)
                .filter(name -> Arrays.stream(Interest.values()).anyMatch(interest -> interest.name().equals(name)))
                .forEach(name -> interests.add(Interest.valueOf(name)));
        return interests;
    }
}
