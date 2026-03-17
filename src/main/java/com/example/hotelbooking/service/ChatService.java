package com.example.hotelbooking.service;

import com.example.hotelbooking.dto.AvailableRoomResponse;
import com.example.hotelbooking.dto.ChatContext;
import com.example.hotelbooking.dto.RoomSearchRequest;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final BookingService bookingService;
    private final RoomRepository roomRepository;
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    public ChatService(BookingService bookingService, RoomRepository roomRepository) {
        this.bookingService = bookingService;
        this.roomRepository = roomRepository;
    }

    public ChatResponse processMessage(String userMessage, ChatContext previousContext) {
        userMessage = userMessage.toLowerCase().trim();
        
        // Extract information from current message
        ChatContext extractedContext = extractSearchParameters(userMessage);
        
        // Merge with previous context
        ChatContext currentContext = previousContext != null ? previousContext.merge(extractedContext) : extractedContext;

        // Nếu không có bất kỳ thông tin nào hữu ích → hỏi gợi ý
        if (currentContext.getNumberOfGuests() == null &&
                currentContext.getCheckInDate() == null &&
                currentContext.getCheckOutDate() == null &&
                currentContext.getRoomType() == null &&
                currentContext.getMaxPricePerNight() == null) {
            return new ChatResponse(
                    "Tôi có thể giúp bạn tìm phòng theo:\n" +
                    "• Số người (ví dụ: 2 người)\n" +
                    "• Ngân sách (ví dụ: tối đa 50$/đêm)\n" +
                    "• Hoặc cả hai (ví dụ: phòng cho 2 người, giá dưới 60$/đêm)",
                    currentContext,
                    false
            );
        }

        // Nếu chỉ có budget (chưa có số người) → tìm theo budget trước
        if (currentContext.getNumberOfGuests() == null &&
                currentContext.getMaxPricePerNight() != null) {
            return searchRoomsByBudget(currentContext);
        }

        // Nếu đã có số người → có thể kết hợp với budget / loại phòng
        if (currentContext.getNumberOfGuests() != null) {
            // If we have dates, use full search (có thể kết hợp với budget)
            if (currentContext.getCheckInDate() != null && currentContext.getCheckOutDate() != null) {
                return searchRoomsWithDates(currentContext);
            }

            // Nếu chỉ có số khách (và có thể có loại phòng / budget) → tìm phòng phù hợp theo sức chứa + budget
            return searchRoomsByCapacity(currentContext);
        }

        // Trường hợp còn lại (ví dụ chỉ có loại phòng) → yêu cầu bổ sung rõ hơn
        return new ChatResponse(
                "Bạn vui lòng cho biết thêm:\n" +
                "• Số lượng khách (ví dụ: 2 người)\n" +
                "hoặc\n" +
                "• Ngân sách tối đa mỗi đêm (ví dụ: tối đa 50$/đêm)",
                currentContext,
                false
        );
    }

    private ChatResponse searchRoomsWithDates(ChatContext context) {
        try {
            RoomSearchRequest searchRequest = new RoomSearchRequest();
            searchRequest.setCheckInDate(context.getCheckInDate());
            searchRequest.setCheckOutDate(context.getCheckOutDate());
            searchRequest.setNumberOfGuests(context.getNumberOfGuests());
            searchRequest.setRoomType(context.getRoomType());
            
            List<AvailableRoomResponse> rooms = bookingService.searchAvailableRooms(searchRequest);

            // Lọc theo ngân sách nếu có
            if (context.getMaxPricePerNight() != null) {
                BigDecimal maxPrice = context.getMaxPricePerNight();
                rooms = rooms.stream()
                        .filter(r -> r.getPricePerNight() != null &&
                                r.getPricePerNight().compareTo(maxPrice) <= 0)
                        .collect(Collectors.toList());
            }
            
            if (rooms.isEmpty()) {
                StringBuilder msg = new StringBuilder();
                msg.append(String.format(
                        "Rất tiếc, không có phòng trống từ %s đến %s cho %d khách",
                        formatDate(context.getCheckInDate()),
                        formatDate(context.getCheckOutDate()),
                        context.getNumberOfGuests()
                ));
                if (context.getRoomType() != null) {
                    msg.append(" (loại: ").append(context.getRoomType()).append(")");
                }
                if (context.getMaxPricePerNight() != null) {
                    msg.append(String.format(" trong khoảng giá tối đa $%.2f/đêm",
                            context.getMaxPricePerNight().doubleValue()));
                }
                msg.append(".");

                return new ChatResponse(msg.toString(), context, false);
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format(
                    "Tìm thấy %d phòng trống từ %s đến %s",
                    rooms.size(),
                    formatDate(context.getCheckInDate()),
                    formatDate(context.getCheckOutDate())
            ));
            if (context.getMaxPricePerNight() != null) {
                response.append(String.format(" với giá tối đa $%.2f/đêm",
                        context.getMaxPricePerNight().doubleValue()));
            }
            response.append(":\n\n");

            for (int i = 0; i < Math.min(rooms.size(), 5); i++) {
                AvailableRoomResponse room = rooms.get(i);
                response.append(String.format(
                    "🏨 Phòng %s - %s\n" +
                    "   💰 Giá: $%.2f/đêm (Tổng: $%.2f cho %d đêm)\n" +
                    "   👥 Sức chứa: %d khách\n" +
                    "   📝 %s\n\n",
                    room.getRoomNumber(),
                    room.getRoomType(),
                    room.getPricePerNight().doubleValue(),
                    room.getTotalPrice().doubleValue(),
                    room.getNumberOfNights(),
                    room.getCapacity(),
                    room.getDescription() != null ? room.getDescription() : "Không có mô tả"
                ));
            }

            if (rooms.size() > 5) {
                response.append(String.format("... và %d phòng khác.\n", rooms.size() - 5));
            }

            return new ChatResponse(response.toString(), rooms, true, context);
        } catch (Exception e) {
            return new ChatResponse(
                "Xin lỗi, đã có lỗi xảy ra khi tìm phòng. Vui lòng thử lại sau.",
                context,
                false
            );
        }
    }

    private ChatResponse searchRoomsByCapacity(ChatContext context) {
        try {
            List<Room> rooms = roomRepository.findRoomsByCapacityAndType(
                    context.getNumberOfGuests(),
                    context.getRoomType()
            );

            // Lọc theo ngân sách nếu có
            if (context.getMaxPricePerNight() != null) {
                BigDecimal maxPrice = context.getMaxPricePerNight();
                rooms = rooms.stream()
                        .filter(r -> r.getPricePerNight() != null &&
                                r.getPricePerNight().compareTo(maxPrice) <= 0)
                        .collect(Collectors.toList());
            }
            
            if (rooms.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append(String.format(
                        "Không tìm thấy phòng nào cho %d khách",
                        context.getNumberOfGuests()
                ));
                if (context.getRoomType() != null) {
                    message.append(" (loại: ").append(context.getRoomType()).append(")");
                }
                if (context.getMaxPricePerNight() != null) {
                    message.append(String.format(" trong khoảng giá tối đa $%.2f/đêm",
                            context.getMaxPricePerNight().doubleValue()));
                }
                message.append(".\n\nVui lòng cung cấp thêm:\n• Ngày check-in và check-out để tìm phòng trống cụ thể");
                return new ChatResponse(message.toString(), context, false);
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format(
                    "Tìm thấy %d phòng phù hợp cho %d khách",
                    rooms.size(),
                    context.getNumberOfGuests()
            ));
            if (context.getRoomType() != null) {
                response.append(" (loại: ").append(context.getRoomType()).append(")");
            }
            if (context.getMaxPricePerNight() != null) {
                response.append(String.format(" với giá tối đa $%.2f/đêm",
                        context.getMaxPricePerNight().doubleValue()));
            }
            response.append(":\n\n");

            for (int i = 0; i < Math.min(rooms.size(), 5); i++) {
                Room room = rooms.get(i);
                response.append(String.format(
                    "🏨 Phòng %s - %s\n" +
                    "   💰 Giá: $%.2f/đêm\n" +
                    "   👥 Sức chứa: %d khách\n" +
                    "   📝 %s\n\n",
                    room.getRoomNumber(),
                    room.getRoomType(),
                    room.getPricePerNight().doubleValue(),
                    room.getCapacity(),
                    room.getDescription() != null ? room.getDescription() : "Không có mô tả"
                ));
            }

            if (rooms.size() > 5) {
                response.append(String.format("... và %d phòng khác.\n\n", rooms.size() - 5));
            }
            
            response.append("💡 Để đặt phòng, vui lòng cung cấp:\n");
            response.append("• Ngày check-in (ví dụ: 15/01/2025)\n");
            response.append("• Ngày check-out (ví dụ: 20/01/2025)");

            // Convert Room to AvailableRoomResponse for display
            List<AvailableRoomResponse> roomResponses = rooms.stream()
                .map(room -> {
                    // Use default dates for display (1 night)
                    LocalDate defaultCheckIn = LocalDate.now().plusDays(1);
                    LocalDate defaultCheckOut = defaultCheckIn.plusDays(1);
                    BigDecimal totalPrice = room.getPricePerNight().multiply(BigDecimal.ONE);
                    
                    return new AvailableRoomResponse(
                        room.getId(),
                        room.getRoomNumber(),
                        room.getRoomType(),
                        room.getPricePerNight(),
                        room.getCapacity(),
                        room.getDescription(),
                        totalPrice,
                        1,
                        room.getImageUrl()
                    );
                })
                .collect(Collectors.toList());

            return new ChatResponse(response.toString(), roomResponses, true, context);
        } catch (Exception e) {
            return new ChatResponse(
                "Xin lỗi, đã có lỗi xảy ra khi tìm phòng. Vui lòng thử lại sau.",
                context,
                false
            );
        }
    }

    /**
     * Tìm phòng chỉ theo ngân sách (không yêu cầu số khách).
     * Có thể kết hợp với loại phòng nếu người dùng đã nhập.
     */
    private ChatResponse searchRoomsByBudget(ChatContext context) {
        try {
            BigDecimal maxPrice = context.getMaxPricePerNight();
            if (maxPrice == null) {
                return new ChatResponse(
                        "Vui lòng nhập ngân sách tối đa, ví dụ: 50$ hoặc \"giá tối đa 50\".",
                        context,
                        false
                );
            }

            // Lấy tất cả phòng còn available
            List<Room> rooms = roomRepository.findAllAvailableRooms();

            // Lọc theo loại phòng (nếu có)
            if (context.getRoomType() != null) {
                String type = context.getRoomType();
                rooms = rooms.stream()
                        .filter(r -> r.getRoomType() != null &&
                                r.getRoomType().equalsIgnoreCase(type))
                        .collect(Collectors.toList());
            }

            // Lọc theo ngân sách
            rooms = rooms.stream()
                    .filter(r -> r.getPricePerNight() != null &&
                            r.getPricePerNight().compareTo(maxPrice) <= 0)
                    .collect(Collectors.toList());

            if (rooms.isEmpty()) {
                StringBuilder msg = new StringBuilder();
                msg.append("Không tìm thấy phòng nào");
                if (context.getRoomType() != null) {
                    msg.append(" loại ").append(context.getRoomType());
                }
                msg.append(String.format(" với giá ≤ $%.2f/đêm.", maxPrice.doubleValue()));
                msg.append("\n\nBạn có thể:\n");
                msg.append("• Tăng ngân sách lên một chút\n");
                msg.append("• Hoặc cho tôi biết thêm số lượng khách để gợi ý chính xác hơn");
                return new ChatResponse(msg.toString(), context, false);
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format(
                    "Tìm thấy %d phòng với giá ≤ $%.2f/đêm",
                    rooms.size(),
                    maxPrice.doubleValue()
            ));
            if (context.getRoomType() != null) {
                response.append(" (loại: ").append(context.getRoomType()).append(")");
            }
            response.append(":\n\n");

            for (int i = 0; i < Math.min(rooms.size(), 5); i++) {
                Room room = rooms.get(i);
                response.append(String.format(
                        "🏨 Phòng %s - %s\n" +
                        "   💰 Giá: $%.2f/đêm\n" +
                        "   👥 Sức chứa: %d khách\n" +
                        "   📝 %s\n\n",
                        room.getRoomNumber(),
                        room.getRoomType(),
                        room.getPricePerNight().doubleValue(),
                        room.getCapacity(),
                        room.getDescription() != null ? room.getDescription() : "Không có mô tả"
                ));
            }

            if (rooms.size() > 5) {
                response.append(String.format("... và %d phòng khác.\n\n", rooms.size() - 5));
            }

            response.append("💡 Bạn có thể nhập thêm:\n");
            response.append("• Số lượng khách (ví dụ: 2 người) để tôi lọc kỹ hơn\n");
            response.append("• Hoặc ngày check-in/check-out để kiểm tra phòng trống theo ngày");

            // Chuyển sang AvailableRoomResponse để frontend hiển thị
            List<AvailableRoomResponse> roomResponses = rooms.stream()
                    .map(room -> {
                        BigDecimal totalPrice = room.getPricePerNight(); // giả định 1 đêm để hiển thị

                        return new AvailableRoomResponse(
                                room.getId(),
                                room.getRoomNumber(),
                                room.getRoomType(),
                                room.getPricePerNight(),
                                room.getCapacity(),
                                room.getDescription(),
                                totalPrice,
                                1,
                                room.getImageUrl()
                        );
                    })
                    .collect(Collectors.toList());

            return new ChatResponse(response.toString(), roomResponses, true, context);
        } catch (Exception e) {
            return new ChatResponse(
                    "Xin lỗi, đã có lỗi xảy ra khi tìm phòng theo ngân sách. Vui lòng thử lại sau.",
                    context,
                    false
            );
        }
    }

    private ChatContext extractSearchParameters(String message) {
        ChatContext context = new ChatContext();
        
        // Extract dates
        LocalDate checkIn = extractDate(message, "check-in", "từ", "ngày");
        LocalDate checkOut = extractDate(message, "check-out", "đến", "tới");
        
        // If dates not found, try relative dates (today, tomorrow, etc.)
        if (checkIn == null || checkOut == null) {
            LocalDate[] relativeDates = extractRelativeDates(message);
            if (relativeDates[0] != null) checkIn = relativeDates[0];
            if (relativeDates[1] != null) checkOut = relativeDates[1];
        }

        // Extract number of guests - improved pattern matching
        Integer guests = extractNumber(message, "khách", "người", "guest", "person");
        if (guests == null) {
            // Try patterns like "2 người", "2 người", "for 2", etc.
            Pattern numberPattern = Pattern.compile("\\b(\\d+)\\s*(?:người|khách|guest|person|people)");
            Matcher matcher = numberPattern.matcher(message);
            if (matcher.find()) {
                guests = Integer.parseInt(matcher.group(1));
            } else {
                // Look for standalone numbers that might be guests (1-10)
                Pattern standaloneNumber = Pattern.compile("\\b([1-9]|10)\\b");
                matcher = standaloneNumber.matcher(message);
                if (matcher.find()) {
                    int num = Integer.parseInt(matcher.group(1));
                    // Only accept if it's likely a guest count (not a date part)
                    if (!message.contains("ngày") && !message.contains("day") && 
                        !message.contains("/") && !message.contains("-")) {
                        guests = num;
                    }
                }
            }
        }

        // Extract room type - improved matching
        String roomType = extractRoomType(message);

        // Extract budget (ngân sách tối đa mỗi đêm)
        BigDecimal maxPrice = extractBudget(message);

        context.setCheckInDate(checkIn);
        context.setCheckOutDate(checkOut);
        context.setNumberOfGuests(guests);
        context.setRoomType(roomType);
        context.setMaxPricePerNight(maxPrice);

        return context;
    }

    private LocalDate extractDate(String message, String keyword, String... alternatives) {
        // Try to find date patterns
        Pattern datePattern = Pattern.compile(
            "(?:\\b" + keyword + "\\s*:?\\s*|" + 
            String.join("|", alternatives) + "\\s*:?\\s*)?" +
            "(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})"
        );
        Matcher matcher = datePattern.matcher(message);
        
        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                // Try parsing with formatters
                String dateStr = matcher.group(0).replaceAll("\\b" + keyword + "\\s*:?\\s*", "")
                    .replaceAll(String.join("|", alternatives) + "\\s*:?\\s*", "").trim();
                return parseDate(dateStr);
            }
        }
        
        // Try ISO format
        Pattern isoPattern = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
        matcher = isoPattern.matcher(message);
        if (matcher.find()) {
            try {
                return LocalDate.parse(matcher.group(0));
            } catch (Exception e) {
                return null;
            }
        }
        
        return null;
    }

    private LocalDate[] extractRelativeDates(String message) {
        LocalDate today = LocalDate.now();
        LocalDate checkIn = null;
        LocalDate checkOut = null;

        // Check for "today", "tomorrow", "hôm nay", "ngày mai"
        if (message.contains("hôm nay") || message.contains("today")) {
            checkIn = today;
        } else if (message.contains("ngày mai") || message.contains("tomorrow")) {
            checkIn = today.plusDays(1);
        }

        // Try to find number of days/nights
        Pattern daysPattern = Pattern.compile("(\\d+)\\s*(?:đêm|night|ngày|day)");
        Matcher matcher = daysPattern.matcher(message);
        if (matcher.find() && checkIn != null) {
            int nights = Integer.parseInt(matcher.group(1));
            checkOut = checkIn.plusDays(nights);
        } else if (checkIn != null) {
            // Default to 1 night if check-in is set but no duration specified
            checkOut = checkIn.plusDays(1);
        }

        return new LocalDate[]{checkIn, checkOut};
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Continue to next formatter
            }
        }
        return null;
    }

    private Integer extractNumber(String message, String... keywords) {
        Pattern pattern = Pattern.compile(
                "(\\d+)\\s*(?:" + String.join("|", keywords) + ")"
        );
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * Cố gắng trích xuất ngân sách tối đa mỗi đêm từ câu người dùng.
     * Ví dụ: \"tối đa 50\", \"dưới 80$\", \"max 100\", \"budget 70\" hoặc chỉ \"50\".
     */
    private BigDecimal extractBudget(String message) {
        String normalized = message.replace(",", ".").toLowerCase();

        // Có từ khóa liên quan đến giá / tiền
        Pattern keywordPattern = Pattern.compile(
                "(?:giá|price|tiền|budget|dưới|tối đa|max)\\s*:?\\s*(\\d+(?:\\.\\d+)?)"
        );
        Matcher matcher = keywordPattern.matcher(normalized);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }

        // Các dạng có ký hiệu tiền tệ: $50, 50$, 50usd
        Pattern currencyPattern = Pattern.compile(
                "\\$\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?:usd|vnd|đ|d)"
        );
        matcher = currencyPattern.matcher(normalized);
        if (matcher.find()) {
            String num = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (num != null) {
                return new BigDecimal(num);
            }
        }

        // Nếu chỉ có một số > 10 trong câu, và không có dấu hiệu đó là ngày → coi là budget
        Pattern loneNumberPattern = Pattern.compile("\\b(\\d{2,4})\\b");
        matcher = loneNumberPattern.matcher(normalized);
        if (matcher.find()) {
            String numStr = matcher.group(1);
            // Tránh các số giống năm trong ngữ cảnh ngày tháng (có dấu / hoặc -)
            if (!normalized.contains("/") && !normalized.contains("-")) {
                try {
                    return new BigDecimal(numStr);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }

    private String extractRoomType(String message) {
        String[] roomTypes = {"single", "double", "suite", "family"};
        String[] vietnameseTypes = {"đơn", "đôi", "suit", "gia đình", "single", "double"};
        
        for (int i = 0; i < roomTypes.length; i++) {
            if (message.contains(roomTypes[i]) || (i < vietnameseTypes.length && message.contains(vietnameseTypes[i]))) {
                return roomTypes[i].substring(0, 1).toUpperCase() + roomTypes[i].substring(1);
            }
        }
        return null;
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // Inner class for chat response
    public static class ChatResponse {
        private String message;
        private List<AvailableRoomResponse> rooms;
        private boolean hasResults;
        private ChatContext context;

        public ChatResponse(String message, List<AvailableRoomResponse> rooms, boolean hasResults) {
            this.message = message;
            this.rooms = rooms;
            this.hasResults = hasResults;
        }

        public ChatResponse(String message, ChatContext context, boolean hasResults) {
            this.message = message;
            this.context = context;
            this.hasResults = hasResults;
            this.rooms = new ArrayList<>();
        }

        public ChatResponse(String message, List<AvailableRoomResponse> rooms, boolean hasResults, ChatContext context) {
            this.message = message;
            this.rooms = rooms;
            this.hasResults = hasResults;
            this.context = context;
        }

        public String getMessage() {
            return message;
        }

        public List<AvailableRoomResponse> getRooms() {
            return rooms;
        }

        public boolean isHasResults() {
            return hasResults;
        }

        public ChatContext getContext() {
            return context;
        }
    }
}
