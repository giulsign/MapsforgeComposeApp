# Point Marker Census V.1.0.2

## Description

4STL Point Marker Census is an Android application designed to allow users to save and manage their geographic coordinates (waypoints) on an offline map. The app is ideal for hikers, travelers, and anyone who needs to keep track of specific locations without an internet connection.

The application is developed in Kotlin and uses Jetpack Compose for a modern and responsive user interface. Offline maps are handled by the MapsForge library, with data provided by OpenStreetMap.

## Main Features

- **GPS Position Saving**: Save your current location with a single tap.
- **Offline Maps**: Use offline maps for navigation without a data connection.
- **Waypoint Management**: View, manage, and export your saved waypoints.
- **No Connection Required**: All main features operate offline.
- **Data Export**: Export your waypoints in JSON format for backup or sharing.

## Technologies Used

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Maps**: MapsForge (with data from OpenStreetMap)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Asynchrony**: Kotlin Coroutines

## Installation

To compile and run the project, you will need Android Studio. Clone the repository and open the project in Android Studio. The necessary dependencies will be downloaded automatically by Gradle.

```bash
git clone https://github.com/your-username/fourSTLPositionMarker.git
```

## License

The source code of this project is released under a proprietary license. For more details, see the [LICENSE](LICENSE) file.

Third-party libraries and resources used in this project are subject to their respective licenses. Special thanks go to:

- **OpenStreetMap**: For the map data.
- **MapsForge**: For the map rendering library.

For more details on third-party licenses, see the [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) file.

## Modification

Modifications by version

- Version 1.0.2
  - Changed Polling service for gps sharing service from Firebase to PointMarker.it php service. Shared positions are saved on server by the activation time.

## Contacts

- **Developer**: Giuliano Signorelli
- **Website**: [www.PointMarker.it](http://www.4stl.it)
- **Email**: [postmaster@pointmarker.it](mailto:postmaster@4stl.it)
- **License:** GPLv3 with Proprietary Branding Notice