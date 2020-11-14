# TeamspeakStatistics
A simple teamspeak statistics collector. The program joins as a server query and writes every event it registers into a database.

Users who want to participate have to write !register in the channel "Orakel". Unregistering can be done using !unregister. Only events of registered users are recorded.

## Configuration
The properties files in the src/main/resources folders have to be configured with real database and teamspeak query connections

## Recorded events
- User join (with timestamp, to which channel)
- User disconnect (with timestamp)
- Client movec (with timestamp, who was moved, who moved)
