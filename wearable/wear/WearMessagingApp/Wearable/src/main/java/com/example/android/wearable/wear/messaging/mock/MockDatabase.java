/*
 * Copyright (c) 2017 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.example.android.wearable.wear.messaging.mock;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.SharedPreferencesHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Mock database stores data in {@link android.content.SharedPreferences} */
public class MockDatabase {

    private static final String TAG = "MockDatabase";

    private static Context mContext;

    /** A callback for events when retrieving a user asynchronously */
    public interface RetrieveUserCallback {
        void retrieved(Profile user);

        void error(Exception e);
    }

    /** A callback for getting events during the creation of a user */
    public interface CreateUserCallback {
        void onSuccess();

        void onError(Exception e);
    }

    /**
     * Initializes the mock database with a context for use with {@link
     * android.content.SharedPreferences}
     */
    public static void init(Context context) {
        mContext = context;
    }

    /**
     * Creates a chat and stores it in the mock database
     *
     * @param participants of the chat
     * @param user that has started the chat
     * @return a chat with information attached to it
     */
    public static Chat createChat(Collection<Profile> participants, Profile user) {
        int size = participants.size();
        Log.d(TAG, String.format("Creating a new chat with %d participant(s)", size));

        Chat chat = new Chat();

        // Initializes chat's last message to a blank String.
        Message message = new Message.Builder().senderId(user.getId()).text("").build();

        chat.setLastMessage(message);

        Map<String, Profile> participantMap = new HashMap<>();
        for (Profile profile : participants) {
            participantMap.put(profile.getId(), profile);
        }
        chat.setParticipantsAndAlias(participantMap);

        // Create an id for the chat based on the aggregate of the participants' ids
        chat.setId(concat(participantMap.keySet()));

        // If you start a new chat with someone you already have a chat with, reuse that chat
        Collection<Chat> allChats = getAllChats();
        Chat exists = findChat(allChats, chat.getId());
        if (exists != null) {
            chat = exists;
        } else {
            allChats.add(chat);
        }

        persistsChats(allChats);

        return chat;
    }

    private static void persistsChats(Collection<Chat> chats) {
        try {
            SharedPreferencesHelper.writeChatsToJsonPref(mContext, new ArrayList<>(chats));
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Could not write chats to json", e);
        }
    }

    @Nullable
    private static Chat findChat(Collection<Chat> chats, String chatId) {
        for (Chat c : chats) {
            if (c.getId().equals(chatId)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Returns all of the chats stored in {@link android.content.SharedPreferences}. An empty {@link
     * Collection<Chat>} will be returned if preferences cannot be read from or cannot parse the
     * json object.
     *
     * @return a collection of chats
     */
    public static Collection<Chat> getAllChats() {

        try {
            return SharedPreferencesHelper.readChatsFromJsonPref(mContext);
        } catch (IOException e) {
            Log.e(TAG, "Could not read/unmarshall the list of chats from shared preferences", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns a {@link Chat} object with a given id.
     *
     * @param id of the stored chat
     * @return chat with id or null if no chat has that id
     */
    @Nullable
    public static Chat findChatById(String id) {
        return findChat(getAllChats(), id);
    }

    /**
     * Updates the {@link Chat#lastMessage} field in the stored json object.
     *
     * @param chat to be updated.
     * @param lastMessage to be updated on the chat.
     */
    public static void updateLastMessage(Chat chat, Message lastMessage) {
        Collection<Chat> chats = getAllChats();
        // Update reference of chat to what it is the mock database.
        chat = findChat(chats, chat.getId());
        if (chat != null) {
            chat.setLastMessage(lastMessage);
        }

        // Save all chats since share prefs are managing them as one entity instead of individually.
        persistsChats(chats);
    }

    /**
     * Flattens the collection of strings into a single string. For example,
     *
     * <p>Input: ["a", "b", "c"]
     *
     * <p>Output: "abc"
     *
     * @param collection to be flattened into a string
     * @return a concatenated string
     */
    @NonNull
    private static String concat(Collection<String> collection) {
        Set<String> participantIds = new TreeSet<>(collection);
        StringBuilder sb = new StringBuilder();
        for (String id : participantIds) {
            sb.append(id);
        }
        return sb.toString();
    }

    /**
     * Saves the message to the thread of messages for the given chat. The message's sent time will
     * also be updated to preserve order.
     *
     * @param chat that the message should be added to.
     * @param message that was sent in the chat.
     * @return message with {@link Message#sentTime} updated
     */
    public static Message saveMessage(Chat chat, Message message) {

        message.setSentTime(System.currentTimeMillis());

        updateLastMessage(chat, message);

        Collection<Message> messages = getAllMessagesForChat(chat.getId());
        messages.add(message);

        try {
            SharedPreferencesHelper.writeMessagesForChatToJsonPref(
                    mContext, chat, new ArrayList<>(messages));
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Could not write the list of messages to shared preferences", e);
        }

        return message;
    }

    /**
     * Returns all messages related to a given chat.
     *
     * @param chatId of the conversation
     * @return messages in the conversation
     */
    public static Collection<Message> getAllMessagesForChat(String chatId) {
        try {
            return SharedPreferencesHelper.readMessagesForChat(mContext, chatId);
        } catch (IOException e) {
            Log.e(TAG, "Could not read/unmarshall the list of messages from shared preferences", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns message details for a message in a particular chat.
     *
     * @param chatId that the message is in
     * @param messageId of the message to be found in the chat
     * @return message from a chat
     */
    @Nullable
    public static Message findMessageById(String chatId, String messageId) {
        for (Message message : getAllMessagesForChat(chatId)) {
            if (message.getId().equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    /**
     * Generates a set of predefined dummy contacts. You may need to add in extra logic for
     * timestamp changes between server and local app.
     *
     * @return a list of profiles to be used as contacts
     */
    public static List<Profile> getUserContacts() {

        try {
            List<Profile> contacts = SharedPreferencesHelper.readContactsFromJsonPref(mContext);
            if (!contacts.isEmpty()) {
                return contacts;
            }
        } catch (IOException e) {
            String logMessage =
                    "Could not read/unmarshall the list of contacts from shared preferences. "
                            + "Returning mock contacts.";
            Log.e(TAG, logMessage, e);
        }

        // Cannot find contacts so we will persist and return a default set of contacts.
        List<Profile> defaultContacts = MockObjectGenerator.generateDefaultContacts();
        try {
            Log.d(TAG, "saving default contacts");
            SharedPreferencesHelper.writeContactsToJsonPref(mContext, defaultContacts);
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Could not write the list of contacts to shared preferences", e);
        }
        return defaultContacts;
    }

    /**
     * Returns the user asynchronously to the client via a callback.
     *
     * @param id for a user
     * @param callback used for handling asynchronous responses
     */
    public static void getUser(String id, RetrieveUserCallback callback) {
        Profile user = null;
        try {
            user = SharedPreferencesHelper.readUserFromJsonPref(mContext);
        } catch (IOException e) {
            Log.e(TAG, "Could not read/unmarshall the user from shared preferences.", e);
            callback.error(e);
        }
        if (user != null && user.getId().equals(id)) {
            callback.retrieved(user);
        } else {
            // Could not find user with that id.
            callback.retrieved(null);
        }
    }

    /**
     * Creates a user asynchronously and notifies the client if a user has been created successfully
     * or if there were any errors.
     *
     * @param user that needs to be created
     * @param callback used for handling asynchronous responses
     */
    public static void createUser(Profile user, CreateUserCallback callback) {
        try {
            SharedPreferencesHelper.writeUserToJsonPref(mContext, user);
            callback.onSuccess();
        } catch (JsonProcessingException e) {
            Log.e(TAG, "Could not write the user to shared preferences", e);
            //Let the client know that the user cannot be saved
            callback.onError(e);
        }
    }
}
