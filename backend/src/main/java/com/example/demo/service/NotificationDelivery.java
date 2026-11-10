package com.example.demo.service;

import com.example.demo.dto.response.NotificationResponse;

public record NotificationDelivery(String recipientName, NotificationResponse notification) {
}
