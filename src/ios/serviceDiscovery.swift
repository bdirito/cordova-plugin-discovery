import Network

@objc(serviceDiscovery) class serviceDiscovery : CDVPlugin {
    @objc(getNetworkServices:) func getNetworkServices(_ command: CDVInvokedUrlCommand) {
    var pluginResult = CDVPluginResult(
      status: CDVCommandStatus_ERROR
    )

        guard let multicast = try? NWMulticastGroup(for:
            [ .hostPort(host: "239.255.255.250", port: 1900) ])
            else { fatalError("error") }
        let group = NWConnectionGroup(with: multicast, using: .udp)
        group.setReceiveHandler(maximumMessageSize: 16384, rejectOversizedMessages: true) { (message, content, isComplete) in
            if((content) != nil) {
                let str = String(decoding: content!  , as: UTF8.self)
                print("Received message from \(String(describing: message.remoteEndpoint)) \(String(describing: str))")
            }
        }
        
        group.stateUpdateHandler = { (newState) in
            print("Group entered state \(String(describing: newState))")
        }
        group.start(queue: .main)
        
        let mainQueue = DispatchQueue.main
        let deadline = DispatchTime.now() + .seconds(10)
        mainQueue.asyncAfter(deadline: deadline) {
            group.cancel()
        }
        
        pluginResult = CDVPluginResult(
          status: CDVCommandStatus_OK,
          messageAs: ""
        )
      self.commandDelegate!.send(
      pluginResult,
      callbackId: command.callbackId
    )
  }
}
