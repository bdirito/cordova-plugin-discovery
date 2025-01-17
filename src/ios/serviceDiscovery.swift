import Network

func processResponse(message:String)-> [String: String]
{
    var msgLines = message.components(separatedBy: "\r");

    var data: [String: String] = [:]


    for i in 0..<msgLines.count {
        var items = msgLines[i].split(separator: ":", maxSplits: 1);

        if(items.count == 2){
            var key = items[0].trimmingCharacters(in: .whitespacesAndNewlines);
            var value = items[1].trimmingCharacters(in: .whitespacesAndNewlines);
            data[key] = value;
        }
    }
    return data;
}

@objc(serviceDiscovery) class serviceDiscovery : CDVPlugin {
    
    @objc(getNetworkServices:) func getNetworkServices(_ command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR
        )
        
        guard let multicast = try? NWMulticastGroup(for:
                                                        [ .hostPort(host: "239.255.255.250", port: 1900) ])
        else {
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            );
            return;
        }
        let group = NWConnectionGroup(with: multicast, using: .udp)
        //Dictionary of Locations to SSDP Service data dictionary (see processResponse)
        var services: [String:[String:String]] = [:]
        group.setReceiveHandler(maximumMessageSize: 16384, rejectOversizedMessages: true) { (message, content, isComplete) in
            if((content) != nil) {
                let str = String(decoding: content!  , as: UTF8.self)
                var data = processResponse(message: str);
                var nt = data["NT"]
                var ip = data["Location"]
                if (nt == nil) || (ip == nil) || nt != command.arguments[0] as? String {
                    return;
                }
                
                services[ip!] = data;
                print("Received message from \(String(describing: message.remoteEndpoint)) \(String(describing: str))")
            }
        }
        
        group.stateUpdateHandler = { (newState) in
            print("Multicast Group entered state \(String(describing: newState))")
        }
        group.start(queue: .main)
        
        let mainQueue = DispatchQueue.main
        //7.5 seconds is half of 15 seconds which is the observed timeout of the Amp upnp in the office
        let deadline = DispatchTime.now() + .milliseconds(7500)
        mainQueue.asyncAfter(deadline: deadline) {
            group.cancel()
            let ret: [[String:String]] = services.values.compactMap { $0 };
            pluginResult = CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAs:ret
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: command.callbackId
            )
        }
    }
}
