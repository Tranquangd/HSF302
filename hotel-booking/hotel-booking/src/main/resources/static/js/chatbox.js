// AI Chatbox JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const chatMessages = document.getElementById('chatMessages');
    const messageInput = document.getElementById('messageInput');
    const sendButton = document.getElementById('sendButton');
    
    let chatContext = null; // Store conversation context

    // Auto-scroll to bottom
    function scrollToBottom() {
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    // Add message to chat
    function addMessage(text, isUser = false, rooms = null) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isUser ? 'user-message' : 'bot-message'}`;
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = isUser ? '👤' : '🤖';
        
        const content = document.createElement('div');
        content.className = 'message-content';
        
        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        
        // Format text with line breaks
        textDiv.innerHTML = text.replace(/\n/g, '<br>');
        
        content.appendChild(textDiv);
        
        // Add room results if available
        if (rooms && rooms.length > 0) {
            const resultsDiv = document.createElement('div');
            resultsDiv.className = 'room-results';
            
            rooms.forEach(room => {
                const roomCard = createRoomCard(room);
                resultsDiv.appendChild(roomCard);
            });
            
            content.appendChild(resultsDiv);
        }
        
        const timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        timeDiv.textContent = new Date().toLocaleTimeString('vi-VN', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
        content.appendChild(timeDiv);
        
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(content);
        
        chatMessages.appendChild(messageDiv);
        scrollToBottom();
    }

    // Create room card element
    function createRoomCard(room) {
        const card = document.createElement('div');
        card.className = 'room-card-result';
        
        const header = document.createElement('div');
        header.className = 'room-card-header';
        
        // Make title clickable - link to booking page
        const title = document.createElement('a');
        title.className = 'room-card-title';
        title.href = `/bookings/create?roomId=${room.roomId}`;
        title.textContent = `Phòng ${room.roomNumber}`;
        title.style.textDecoration = 'none';
        title.style.color = 'inherit';
        title.style.cursor = 'pointer';
        title.onmouseover = function() { this.style.textDecoration = 'underline'; };
        title.onmouseout = function() { this.style.textDecoration = 'none'; };
        
        const type = document.createElement('span');
        type.className = 'room-card-type';
        type.textContent = room.roomType;
        
        header.appendChild(title);
        header.appendChild(type);
        
        const details = document.createElement('div');
        details.className = 'room-card-details';
        
        // Make capacity clickable
        const capacity = document.createElement('div');
        capacity.className = 'room-detail-item';
        const capacityLink = document.createElement('a');
        capacityLink.href = `/bookings/create?roomId=${room.roomId}`;
        capacityLink.style.textDecoration = 'none';
        capacityLink.style.color = 'inherit';
        capacityLink.innerHTML = '<strong>👥 Sức chứa:</strong> ' + room.capacity + ' khách';
        capacityLink.onmouseover = function() { this.style.textDecoration = 'underline'; };
        capacityLink.onmouseout = function() { this.style.textDecoration = 'none'; };
        capacity.appendChild(capacityLink);
        
        // Make price clickable
        const pricePerNight = document.createElement('div');
        pricePerNight.className = 'room-detail-item';
        const priceLink = document.createElement('a');
        priceLink.href = `/bookings/create?roomId=${room.roomId}`;
        priceLink.style.textDecoration = 'none';
        priceLink.style.color = 'inherit';
        priceLink.innerHTML = '<strong>💰 Giá/đêm:</strong> $' + room.pricePerNight.toFixed(2);
        priceLink.onmouseover = function() { this.style.textDecoration = 'underline'; };
        priceLink.onmouseout = function() { this.style.textDecoration = 'none'; };
        pricePerNight.appendChild(priceLink);
        
        // Make nights clickable
        const nights = document.createElement('div');
        nights.className = 'room-detail-item';
        const nightsLink = document.createElement('a');
        nightsLink.href = `/bookings/create?roomId=${room.roomId}`;
        nightsLink.style.textDecoration = 'none';
        nightsLink.style.color = 'inherit';
        nightsLink.innerHTML = '<strong>🌙 Số đêm:</strong> ' + room.numberOfNights + ' đêm';
        nightsLink.onmouseover = function() { this.style.textDecoration = 'underline'; };
        nightsLink.onmouseout = function() { this.style.textDecoration = 'none'; };
        nights.appendChild(nightsLink);
        
        details.appendChild(capacity);
        details.appendChild(pricePerNight);
        details.appendChild(nights);
        
        if (room.description) {
            const desc = document.createElement('div');
            desc.className = 'room-detail-item';
            desc.style.gridColumn = '1 / -1';
            const descLink = document.createElement('a');
            descLink.href = `/bookings/create?roomId=${room.roomId}`;
            descLink.style.textDecoration = 'none';
            descLink.style.color = 'inherit';
            descLink.innerHTML = '<strong>📝 Mô tả:</strong> ' + room.description;
            descLink.onmouseover = function() { this.style.textDecoration = 'underline'; };
            descLink.onmouseout = function() { this.style.textDecoration = 'none'; };
            desc.appendChild(descLink);
            details.appendChild(desc);
        }
        
        const priceSection = document.createElement('div');
        priceSection.className = 'room-card-price';
        
        const priceLabel = document.createElement('span');
        priceLabel.className = 'price-label';
        priceLabel.textContent = 'Tổng giá:';
        
        // Make total price clickable
        const priceValue = document.createElement('a');
        priceValue.className = 'price-value';
        priceValue.href = `/bookings/create?roomId=${room.roomId}`;
        priceValue.textContent = '$' + room.totalPrice.toFixed(2);
        priceValue.style.textDecoration = 'none';
        priceValue.style.color = 'inherit';
        priceValue.onmouseover = function() { this.style.textDecoration = 'underline'; };
        priceValue.onmouseout = function() { this.style.textDecoration = 'none'; };
        
        priceSection.appendChild(priceLabel);
        priceSection.appendChild(priceValue);
        
        const actions = document.createElement('div');
        actions.className = 'room-card-actions';
        
        // Add "Xem chi tiết" link
        const viewDetailsButton = document.createElement('a');
        viewDetailsButton.href = `/bookings/search?roomId=${room.roomId}`;
        viewDetailsButton.className = 'btn-book-room';
        viewDetailsButton.style.background = 'var(--secondary-color)';
        viewDetailsButton.textContent = '👁️ Xem chi tiết';
        viewDetailsButton.onmouseover = function() { this.style.background = '#e6a602'; };
        viewDetailsButton.onmouseout = function() { this.style.background = 'var(--secondary-color)'; };
        
        const bookButton = document.createElement('a');
        bookButton.href = `/bookings/create?roomId=${room.roomId}`;
        bookButton.className = 'btn-book-room';
        bookButton.textContent = '📅 Đặt phòng ngay';
        
        actions.appendChild(viewDetailsButton);
        actions.appendChild(bookButton);
        
        card.appendChild(header);
        card.appendChild(details);
        card.appendChild(priceSection);
        card.appendChild(actions);
        
        return card;
    }

    // Show loading indicator
    function showLoading() {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message bot-message';
        messageDiv.id = 'loading-message';
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '🤖';
        
        const content = document.createElement('div');
        content.className = 'message-content';
        
        const textDiv = document.createElement('div');
        textDiv.className = 'message-text';
        textDiv.innerHTML = '<div class="loading">' +
            '<span>Đang tìm phòng</span>' +
            '<div class="loading-dot"></div>' +
            '<div class="loading-dot"></div>' +
            '<div class="loading-dot"></div>' +
            '</div>';
        
        content.appendChild(textDiv);
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(content);
        
        chatMessages.appendChild(messageDiv);
        scrollToBottom();
    }

    // Remove loading indicator
    function removeLoading() {
        const loading = document.getElementById('loading-message');
        if (loading) {
            loading.remove();
        }
    }

    // Send message
    async function sendMessage() {
        const message = messageInput.value.trim();
        if (!message) return;

        // Add user message
        addMessage(message, true);
        messageInput.value = '';
        sendButton.disabled = true;
        
        // Show loading
        showLoading();

        try {
            const requestBody = {
                message: message,
                context: chatContext
            };

            const response = await fetch('/chat/message', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                throw new Error('Network response was not ok');
            }

            const data = await response.json();
            removeLoading();
            
            // Update context from response
            if (data.context) {
                chatContext = data.context;
            }
            
            // Add bot response
            addMessage(data.message, false, data.rooms);
            
        } catch (error) {
            removeLoading();
            addMessage('Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau.', false);
            console.error('Error:', error);
        } finally {
            sendButton.disabled = false;
            messageInput.focus();
        }
    }

    // Event listeners
    sendButton.addEventListener('click', sendMessage);
    
    messageInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // Focus input on load
    messageInput.focus();
});
