/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.items.events;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.core.events.AbstractEventFactory;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.dto.ItemDTO;
import org.eclipse.smarthome.core.items.dto.ItemDTOMapper;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

/**
 * An {@link ItemEventFactory} is responsible for creating item event instances, e.g. {@link ItemCommandEvent}s and
 * {@link ItemStateEvent}s.
 *
 * @author Stefan Bußweiler - Initial contribution
 */
public class ItemEventFactory extends AbstractEventFactory {

    private static final String ITEM_COMAND_EVENT_TOPIC = "smarthome/items/{itemName}/command";

    private static final String ITEM_STATE_EVENT_TOPIC = "smarthome/items/{itemName}/state";

    private static final String ITEM_STATE_CHANGED_EVENT_TOPIC = "smarthome/items/{itemName}/statechanged";

    private static final String GROUPITEM_STATE_CHANGED_EVENT_TOPIC = "smarthome/items/{itemName}/{memberName}/statechanged";

    private static final String ITEM_ADDED_EVENT_TOPIC = "smarthome/items/{itemName}/added";

    private static final String ITEM_REMOVED_EVENT_TOPIC = "smarthome/items/{itemName}/removed";

    private static final String ITEM_UPDATED_EVENT_TOPIC = "smarthome/items/{itemName}/updated";

    private static StateFormatter stateFormatter = new StateFormatter();
    private static CommandFormatter commandFormatter = new CommandFormatter();

    /**
     * Constructs a new ItemEventFactory.
     */
    public ItemEventFactory() {
        super(Sets.newHashSet(ItemCommandEvent.TYPE, ItemStateEvent.TYPE, ItemStateChangedEvent.TYPE,
                ItemAddedEvent.TYPE, ItemUpdatedEvent.TYPE, ItemRemovedEvent.TYPE, GroupItemStateChangedEvent.TYPE));
    }

    @Override
    protected Event createEventByType(String eventType, String topic, String payload, String source) throws Exception {
        Event event = null;
        if (eventType.equals(ItemCommandEvent.TYPE)) {
            event = createCommandEvent(topic, payload, source);
        } else if (eventType.equals(ItemStateEvent.TYPE)) {
            event = createStateEvent(topic, payload, source);
        } else if (eventType.equals(ItemStateChangedEvent.TYPE)) {
            event = createStateChangedEvent(topic, payload);
        } else if (eventType.equals(ItemAddedEvent.TYPE)) {
            event = createAddedEvent(topic, payload);
        } else if (eventType.equals(ItemUpdatedEvent.TYPE)) {
            event = createUpdatedEvent(topic, payload);
        } else if (eventType.equals(ItemRemovedEvent.TYPE)) {
            event = createRemovedEvent(topic, payload);
        } else if (eventType.equals(GroupItemStateChangedEvent.TYPE)) {
            event = createGroupStateChangedEvent(topic, payload);
        }
        return event;
    }

    private Event createGroupStateChangedEvent(String topic, String payload) {
        String itemName = getItemName(topic);
        String memberName = getMemberName(topic);
        ItemStateChangedEventPayloadBean bean = deserializePayload(payload, ItemStateChangedEventPayloadBean.class);
        State state = stateFormatter.parse(bean.getType(), bean.getValue(), bean.getStateMap());
        State oldState = stateFormatter.parse(bean.getOldType(), bean.getOldValue(), bean.getStateMap());
        return new GroupItemStateChangedEvent(topic, payload, itemName, memberName, state, oldState);
    }

    private Event createCommandEvent(String topic, String payload, String source) {
        String itemName = getItemName(topic);
        ItemEventPayloadBean bean = deserializePayload(payload, ItemEventPayloadBean.class);
        Command command = commandFormatter.parse(bean.getType(), bean.getValue());
        return new ItemCommandEvent(topic, payload, itemName, command, source);
    }

    private Event createStateEvent(String topic, String payload, String source) {
        String itemName = getItemName(topic);
        ItemEventPayloadBean bean = deserializePayload(payload, ItemEventPayloadBean.class);
        State state = stateFormatter.parse(bean.getType(), bean.getValue(), bean.getStateMap());
        return new ItemStateEvent(topic, payload, itemName, state, source);
    }

    private Event createStateChangedEvent(String topic, String payload) {
        String itemName = getItemName(topic);
        ItemStateChangedEventPayloadBean bean = deserializePayload(payload, ItemStateChangedEventPayloadBean.class);
        State state = stateFormatter.parse(bean.getType(), bean.getValue(), bean.getStateMap());
        State oldState = stateFormatter.parse(bean.getOldType(), bean.getOldValue(), bean.getOldStateMap());
        return new ItemStateChangedEvent(topic, payload, itemName, state, oldState);
    }

    private String getItemName(String topic) {
        String[] topicElements = getTopicElements(topic);
        if (topicElements.length < 4) {
            throw new IllegalArgumentException("Event creation failed, invalid topic: " + topic);
        }
        return topicElements[2];
    }

    private String getMemberName(String topic) {
        String[] topicElements = getTopicElements(topic);
        if (topicElements.length < 5) {
            throw new IllegalArgumentException("Event creation failed, invalid topic: " + topic);
        }
        return topicElements[3];
    }

    private Event createAddedEvent(String topic, String payload) {
        ItemDTO itemDTO = deserializePayload(payload, ItemDTO.class);
        return new ItemAddedEvent(topic, payload, itemDTO);
    }

    private Event createRemovedEvent(String topic, String payload) {
        ItemDTO itemDTO = deserializePayload(payload, ItemDTO.class);
        return new ItemRemovedEvent(topic, payload, itemDTO);
    }

    private Event createUpdatedEvent(String topic, String payload) {
        ItemDTO[] itemDTOs = deserializePayload(payload, ItemDTO[].class);
        if (itemDTOs.length != 2) {
            throw new IllegalArgumentException("ItemUpdateEvent creation failed, invalid payload: " + payload);
        }
        return new ItemUpdatedEvent(topic, payload, itemDTOs[0], itemDTOs[1]);
    }

    /**
     * Creates an item command event.
     *
     * @param itemName the name of the item to send the command for
     * @param command the command to send
     * @param source the name of the source identifying the sender (can be null)
     *
     * @return the created item command event
     *
     * @throws IllegalArgumentException if itemName or command is null
     */
    public static ItemCommandEvent createCommandEvent(String itemName, Command command, String source) {
        assertValidArguments(itemName, command, "command");
        String topic = buildTopic(ITEM_COMAND_EVENT_TOPIC, itemName);
        ItemEventPayloadBean bean = new ItemEventPayloadBean(commandFormatter.getType(command), command.toString(),
                null);
        String payload = serializePayload(bean);
        return new ItemCommandEvent(topic, payload, itemName, command, source);
    }

    /**
     * Creates an item command event.
     *
     * @param itemName the name of the item to send the command for
     * @param command the command to send
     *
     * @return the created item command event
     *
     * @throws IllegalArgumentException if itemName or command is null
     */
    public static ItemCommandEvent createCommandEvent(String itemName, Command command) {
        return createCommandEvent(itemName, command, null);
    }

    /**
     * Creates an item state event.
     *
     * @param itemName the name of the item to send the state update for
     * @param state the new state to send
     * @param source the name of the source identifying the sender (can be null)
     *
     * @return the created item state event
     *
     * @throws IllegalArgumentException if itemName or state is null
     */
    public static ItemStateEvent createStateEvent(String itemName, State state, String source) {
        assertValidArguments(itemName, state, "state");
        String topic = buildTopic(ITEM_STATE_EVENT_TOPIC, itemName);
        ItemEventPayloadBean bean = new ItemEventPayloadBean(stateFormatter.getType(state),
                stateFormatter.format(state), stateFormatter.getStateMap(state));
        String payload = serializePayload(bean);
        return new ItemStateEvent(topic, payload, itemName, state, source);
    }

    /**
     * Creates an item state event.
     *
     * @param itemName the name of the item to send the state update for
     * @param state the new state to send
     *
     * @return the created item state event
     *
     * @throws IllegalArgumentException if itemName or state is null
     */
    public static ItemStateEvent createStateEvent(String itemName, State state) {
        return createStateEvent(itemName, state, null);
    }

    /**
     * Creates an item state changed event.
     *
     * @param itemName the name of the item to send the state changed event for
     * @param newState the new state to send
     * @param oldState the old state of the item
     *
     * @return the created item state changed event
     *
     * @throws IllegalArgumentException if itemName or state is null
     */
    public static ItemStateChangedEvent createStateChangedEvent(String itemName, State newState, State oldState) {
        assertValidArguments(itemName, newState, "state");
        String topic = buildTopic(ITEM_STATE_CHANGED_EVENT_TOPIC, itemName);
        ItemStateChangedEventPayloadBean bean = new ItemStateChangedEventPayloadBean( //
                stateFormatter.getType(newState), //
                stateFormatter.format(newState), //
                stateFormatter.getStateMap(newState), //
                stateFormatter.getType(oldState), //
                stateFormatter.format(oldState), //
                stateFormatter.getStateMap(oldState));
        String payload = serializePayload(bean);
        return new ItemStateChangedEvent(topic, payload, itemName, newState, oldState);
    }

    public static GroupItemStateChangedEvent createGroupStateChangedEvent(String itemName, String memberName,
            State newState, State oldState) {
        assertValidArguments(itemName, memberName, newState, "state");
        String topic = buildGroupTopic(GROUPITEM_STATE_CHANGED_EVENT_TOPIC, itemName, memberName);
        ItemStateChangedEventPayloadBean bean = new ItemStateChangedEventPayloadBean( //
                stateFormatter.getType(newState), //
                stateFormatter.format(newState), //
                stateFormatter.getStateMap(newState), //
                stateFormatter.getType(oldState), //
                stateFormatter.format(oldState), //
                stateFormatter.getStateMap(oldState));
        String payload = serializePayload(bean);
        return new GroupItemStateChangedEvent(topic, payload, itemName, memberName, newState, oldState);
    }

    /**
     * Creates an item added event.
     *
     * @param item the item
     *
     * @return the created item added event
     *
     * @throws IllegalArgumentException if item is null
     */
    public static ItemAddedEvent createAddedEvent(Item item) {
        assertValidArgument(item, "item");
        String topic = buildTopic(ITEM_ADDED_EVENT_TOPIC, item.getName());
        ItemDTO itemDTO = map(item);
        String payload = serializePayload(itemDTO);
        return new ItemAddedEvent(topic, payload, itemDTO);
    }

    /**
     * Creates an item removed event.
     *
     * @param item the item
     *
     * @return the created item removed event
     *
     * @throws IllegalArgumentException if item is null
     */
    public static ItemRemovedEvent createRemovedEvent(Item item) {
        assertValidArgument(item, "item");
        String topic = buildTopic(ITEM_REMOVED_EVENT_TOPIC, item.getName());
        ItemDTO itemDTO = map(item);
        String payload = serializePayload(itemDTO);
        return new ItemRemovedEvent(topic, payload, itemDTO);
    }

    /**
     * Creates an item updated event.
     *
     * @param item the item
     * @param oldItem the old item
     *
     * @return the created item updated event
     *
     * @throws IllegalArgumentException if item or oldItem is null
     */
    public static ItemUpdatedEvent createUpdateEvent(Item item, Item oldItem) {
        assertValidArgument(item, "item");
        assertValidArgument(oldItem, "oldItem");
        String topic = buildTopic(ITEM_UPDATED_EVENT_TOPIC, item.getName());
        ItemDTO itemDTO = map(item);
        ItemDTO oldItemDTO = map(oldItem);
        List<ItemDTO> itemDTOs = new LinkedList<ItemDTO>();
        itemDTOs.add(itemDTO);
        itemDTOs.add(oldItemDTO);
        String payload = serializePayload(itemDTOs);
        return new ItemUpdatedEvent(topic, payload, itemDTO, oldItemDTO);
    }

    private static String buildTopic(String topic, String itemName) {
        return topic.replace("{itemName}", itemName);
    }

    private static String buildGroupTopic(String topic, String itemName, String memberName) {
        return buildTopic(topic, itemName).replace("{memberName}", memberName);
    }

    private static ItemDTO map(Item item) {
        return ItemDTOMapper.map(item);
    }

    private static void assertValidArguments(String itemName, Type type, String typeArgumentName) {
        Preconditions.checkArgument(itemName != null && !itemName.isEmpty(),
                "The argument 'itemName' must not be null or empty.");
        Preconditions.checkArgument(type != null, "The argument '" + typeArgumentName + "' must not be null or empty.");
    }

    private static void assertValidArguments(String itemName, String memberName, Type type, String typeArgumentName) {
        Preconditions.checkArgument(itemName != null && !itemName.isEmpty(),
                "The argument 'itemName' must not be null or empty.");
        Preconditions.checkArgument(memberName != null && !memberName.isEmpty(),
                "The argument 'memberName' must not be null or empty.");
        Preconditions.checkArgument(type != null, "The argument '" + typeArgumentName + "' must not be null or empty.");
    }

    private static void assertValidArgument(Item item, String argumentName) {
        Preconditions.checkArgument(item != null, "The argument '" + argumentName + "' must no be null.");
    }

    /**
     * This is a java bean that is used to serialize/deserialize item event payload.
     */
    private static class ItemEventPayloadBean {
        private String type;
        private String value;
        private Map<String, String> stateMap;

        /**
         * Default constructor for deserialization e.g. by Gson.
         */
        @SuppressWarnings("unused")
        protected ItemEventPayloadBean() {
        }

        public ItemEventPayloadBean(String type, String value, Map<String, String> stateMap) {
            this.type = type;
            this.value = value;
            this.stateMap = stateMap;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public Map<String, String> getStateMap() {
            return stateMap;
        }

    }

    /**
     * This is a java bean that is used to serialize/deserialize item state changed event payload.
     */
    private static class ItemStateChangedEventPayloadBean {
        private String type;
        private String value;
        private Map<String, String> stateMap;
        private String oldType;
        private String oldValue;
        private Map<String, String> oldStateMap;

        /**
         * Default constructor for deserialization e.g. by Gson.
         */
        @SuppressWarnings("unused")
        protected ItemStateChangedEventPayloadBean() {
        }

        public ItemStateChangedEventPayloadBean(String type, String value, Map<String, String> stateMap, String oldType,
                String oldValue, Map<String, String> oldStateMap) {
            this.type = type;
            this.value = value;
            this.stateMap = stateMap;
            this.oldType = oldType;
            this.oldValue = oldValue;
            this.oldStateMap = oldStateMap;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public Map<String, String> getStateMap() {
            return stateMap;
        }

        public String getOldType() {
            return oldType;
        }

        public String getOldValue() {
            return oldValue;
        }

        public Map<String, String> getOldStateMap() {
            return oldStateMap;
        }
    }
}
