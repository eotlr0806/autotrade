var stompClient = null;

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    }
    else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function connect() {
    var socket = new SockJS('/avit-websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        setConnected(true);
        console.log('Connected: ' + frame);

        stompClient.subscribe('/topic/greetings', function (greeting) {
            showGreeting(JSON.parse(greeting.body).content);
        });

    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    console.log("Disconnected");
}

function sendName() {
    stompClient.send("/app/hello", {}, JSON.stringify({'name': $("#name").val()}));
}

function showGreeting(message) {
    console.log('message : ' + message);
    show_stack_custom_top('success');
    //$("#greetings").append("<tr><td>" + message + "</td></tr>");
}


// Custom top position
function show_stack_custom_top(type) {
    var opts = {
        title: "Over here",
        text: "Check me out. I'm in a different stack.",
        width: "100%",
        cornerclass: "no-border-radius",
        addclass: "stack-custom-top bg-primary",
        stack: stack_custom_top
    };
    switch (type) {

        case 'warning':
            opts.title = "Oh No";
            opts.text = "Watch out for that water tower!";
            opts.addclass = "stack-custom-top bg-warning";
            opts.type = "error";
            break;

        case 'error':
            opts.title = "Oh No";
            opts.text = "Watch out for that water tower!";
            opts.addclass = "stack-custom-top bg-danger";
            opts.type = "error";
            break;

        case 'info':
            opts.title = "Breaking News";
            opts.text = "Have you met Ted?";
            opts.addclass = "stack-custom-top bg-info";
            opts.type = "info";
            break;

        case 'success':
            opts.title = "Good News Everyone";
            opts.text = "I've invented a device that bites shiny metal asses.";
            opts.addclass = "stack-custom-top bg-success";
            opts.type = "success";
            break;
    }
    new PNotify(opts);
}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $( "#connect" ).click(function() { connect(); });
    $( "#disconnect" ).click(function() { disconnect(); });
    $( "#send" ).click(function() { sendName(); });
});