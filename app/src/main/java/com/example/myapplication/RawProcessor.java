package com.example.myapplication;

import android.util.Log;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Xử lý nén ảnh RAW (.DNG) với các mức độ nén khác nhau.
 */
public class RawProcessor {
    private static final String TAG = "RawProcessor";

    public enum CompressionLevel {
        NONE,       // Không nén
        LOSSLESS,   // Nén không mất mát (Huffman)
        LOSSY       // Nén có mất mát (Chroma Subsampling, Quantization)
    }

    public static byte[] processRawData(byte[] rawData, CompressionLevel level) {
        switch (level) {
            case LOSSLESS:
                return compressLossless(rawData);
            case LOSSY:
                return compressLossy(rawData);
            case NONE:
            default:
                return rawData;
        }
    }

    /**
     * Nén không mất mát: Sử dụng Huffman Coding và nhóm các mẫu lặp lại.
     */
    private static byte[] compressLossless(byte[] data) {
        Log.d(TAG, "Đang thực hiện nén không mất mát (Lossless)...");
        
        // 1. Nhóm các mẫu lặp lại (Run-Length Encoding đơn giản)
        // 2. Thuật toán Huffman Coding
        Map<Byte, Integer> frequencies = new HashMap<>();
        for (byte b : data) {
            frequencies.put(b, frequencies.getOrDefault(b, 0) + 1);
        }

        HuffmanNode root = buildHuffmanTree(frequencies);
        Map<Byte, String> huffmanCodes = new HashMap<>();
        generateCodes(root, "", huffmanCodes);

        // Logic thực tế sẽ chuyển đổi bytes sang chuỗi bit dựa trên huffmanCodes
        // Ở đây chúng ta trả về dữ liệu tượng trưng cho quá trình nén
        return data; // Placeholder: Trong thực tế sẽ trả về bitstream đã nén
    }

    /**
     * Nén có mất mát: Lược bỏ chi tiết tần số cao, giảm độ sâu màu, Chroma Subsampling.
     */
    private static byte[] compressLossy(byte[] data) {
        Log.d(TAG, "Đang thực hiện nén có mất mát (Lossy)...");

        // 1. Lược bỏ chi tiết tần số cao (High-frequency details)
        // Thường thực hiện qua DCT (Discrete Cosine Transform) và Quantization
        
        // 2. Giảm độ sâu màu (Color depth reduction)
        // Ví dụ: Từ 14-bit xuống 10-bit phi tuyến tính
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((data[i] & 0xFF) >> 2); // Giảm 2 bit (Ví dụ đơn giản)
        }

        // 3. Chroma Subsampling (Lấy mẫu phụ sắc độ)
        // Giảm thông tin màu sắc (Cb, Cr) vì mắt người ít nhạy cảm hơn độ sáng (Y)
        
        return data;
    }

    // --- Hỗ trợ Huffman Coding ---

    private static class HuffmanNode {
        byte data;
        int frequency;
        HuffmanNode left, right;

        HuffmanNode(byte data, int frequency) {
            this.data = data;
            this.frequency = frequency;
        }
    }

    private static HuffmanNode buildHuffmanTree(Map<Byte, Integer> frequencies) {
        PriorityQueue<HuffmanNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.frequency));
        for (Map.Entry<Byte, Integer> entry : frequencies.entrySet()) {
            pq.add(new HuffmanNode(entry.getKey(), entry.getValue()));
        }

        while (pq.size() > 1) {
            HuffmanNode left = pq.poll();
            HuffmanNode right = pq.poll();
            HuffmanNode parent = new HuffmanNode((byte) 0, left.frequency + right.frequency);
            parent.left = left;
            parent.right = right;
            pq.add(parent);
        }
        return pq.poll();
    }

    private static void generateCodes(HuffmanNode node, String code, Map<Byte, String> codes) {
        if (node == null) return;
        if (node.left == null && node.right == null) {
            codes.put(node.data, code);
        }
        generateCodes(node.left, code + "0", codes);
        generateCodes(node.right, code + "1", codes);
    }
}
