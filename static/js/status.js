// Copyright (c) 2012, Code Aurora Forum. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
// * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
// * Neither the name of Code Aurora Forum, Inc. nor the names of its
// contributors may be used to endorse or promote products derived
// from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
// BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
// WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
// IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

$(document).ready(function() {
	makeStatusTable();
	window.setInterval(function() {
		makeStatusTable();
	}, 5000);
});

function makeStatusTable() {
	var peerStatusTable = $("<table class='peerStatusTable'></table>")
	peerStatusTable
			.html($("<tr><th>#</th><th>Id</th><th>Last Seen</th><th>Address</th><th>Port</th></tr>"));

	var evictStatusTable = $("<table class='peerStatusTable'></table>")
	evictStatusTable
			.html($("<tr><th>#</th><th>Plugin</th><th>Cache</th><th>Key</th></tr>"));

	$.getJSON('../status/json/', function(data) {
		var outdated_threshold = data.outdatedThreshold;

		var selfReceiveHost = $
				.parseJSON(data.self.properties.receiveHost.string);
		var selfReceivePort = $
				.parseJSON(data.self.properties.receivePort.string);

		peerStatusTable.append("<tr><td>Self</td><td>" + data.self.id.string
				+ "</td><td>-</td><td>" + selfReceiveHost + "</td><td>"
				+ selfReceivePort + "</td></tr>");

		data.activities.sort(function(a, b) {
			if (a.peer.id == b.peer.id) {
				return 0;
			}

			return b.lastSeen - a.lastSeen;
		});

		$.each(data.activities, function(index, activity) {
			var receiveHost = $
					.parseJSON(activity.peer.properties.receiveHost.string);
			var receivePort = $
					.parseJSON(activity.peer.properties.receivePort.string);

			var date = new Date(activity.lastSeen);

			var status_row = $("<tr><td>" + index + "</td><td>"
					+ activity.peer.id.string + "</td><td>" + date.toString()
					+ "</td><td>" + receiveHost + "</td><td>" + receivePort
					+ "</td></tr>");

			if (Date.now() - activity.lastSeen < outdated_threshold) {
				status_row.css('background-color', "#B6DB49");
			} else {
				status_row.css('background-color', "#FF7979");
			}

			peerStatusTable.append(status_row)
		});

		$.each(data.recentEvicts,
				function(index, evict) {
					var status_row = $("<tr><td>" + evict.pluginName
							+ "</td><td>" + evict.cacheName + "</td><td>"
							+ evict.key + "</td></tr>");

					evictStatusTable.append(status_row)
				});

		$("#wrapper").html(peerStatusTable);
		$("#wrapper").append(evictStatusTable);
	});
}