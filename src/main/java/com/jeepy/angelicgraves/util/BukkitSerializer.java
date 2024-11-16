package com.jeepy.angelicgraves.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class BukkitSerializer {

    public static String itemStackQueueToBase64(Queue<ItemStack> queue) throws IOException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the queue
            dataOutput.writeInt(queue.size());

            // Save every element in the queue
            for (ItemStack item : queue) {
                dataOutput.writeObject(item);
            }

            // Serialize that array
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IOException("Unable to save queue", e);
        }
    }

    public static Queue<ItemStack> itemStackQueueFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            Queue<ItemStack> queue = new LinkedList<>();

            // Read the size of the queue
            int size = dataInput.readInt();

            // Read in all the ItemStacks from the queue
            for (int i = 0; i < size; i++) {
                queue.add((ItemStack) dataInput.readObject());
            }

            dataInput.close();
            return queue;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type", e);
        }
    }
}