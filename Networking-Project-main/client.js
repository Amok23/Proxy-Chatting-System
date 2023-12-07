// Function to get query parameters from the URL
function getQueryParam(key) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get(key);
}
document.addEventListener("DOMContentLoaded", function () {
    const proxyIp = getQueryParam("proxyIp");

    if (proxyIp) {
        const proxyIpElement = document.getElementById("proxyIp");

        if (proxyIpElement) {
            proxyIpElement.textContent = proxyIp;
        } else {
            console.error("Element with ID 'proxyIp' not found.");
        }
    }

    if (window.location.pathname === "/index.html") {
        // Function to handle form submission when connecting to the proxy
        function handleSubmit(event) {
            event.preventDefault();
            const ipInput = document.getElementById("IP");
            const usernameInput = document.getElementById("username");
            const proxyIp = ipInput.value.trim();
            const username = usernameInput.value.trim();

            if (proxyIp && username) {
                console.log("Proxy IP: " + proxyIp);
                console.log("Username: " + username);

                // Redirect the user to the chat interface page
                window.location.href = "chatmsg.html?proxyIp=" + encodeURIComponent(proxyIp);
            }
        }

        const connectForm = document.getElementById("connectForm");

        if (connectForm) {
            connectForm.addEventListener("submit", handleSubmit);
        }
    } else if (window.location.pathname === "/chatmsg.html") {
        // Function to send a message
        function sendMessage() {
            const messageInput = document.getElementById("messageInput");
            const message = messageInput.value.trim();
            function sendMessage() {
                const messageInput = document.getElementById("messageInput");
                const message = messageInput.value.trim();

            }


            if (message) {
                // Send the message to the server 
                displayMessage("You: " + message);
                messageInput.value = "";
            }
        }

        const sendButton = document.getElementById("sendButton");

        if (sendButton) {
            sendButton.addEventListener("click", sendMessage);
        }


    }
    function displayMessage(message) {
        const chatbox = document.querySelector(".chatbox");
        const chatMessage = document.createElement("div");
        chatMessage.classList.add("chat");
        chatMessage.textContent = message;
        chatbox.appendChild(chatMessage);
        //scroll the chatbox to the bottom to show the latest messages
        chatbox.scrollTop = chatbox.scrollHeight;
    }

    
});
