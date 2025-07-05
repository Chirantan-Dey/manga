# Task History: Suwayomi Server and WebUI Analysis

## Task Overview
The primary task was to analyze the `Suwayomi-Server` and `Suwayomi-WebUI` directories to provide a structured, detailed report including documentation insights, type definitions, implementations, and critical dependencies.

## Key Findings

### Suwayomi-Server Analysis
*   **File Structure**: The `Suwayomi-Server` project is primarily a Kotlin application, with a well-defined package structure under `suwayomi.tachidesk`. Key subdirectories include `global`, `graphql`, `i18n`, `manga`, `opds`, `server`, and `util`.
*   **Core Component**: The `GraphQLController.kt` file at [`Suwayomi-Server/server/src/main/kotlin/suwayomi/tachidesk/graphql/controller/GraphQLController.kt`](Suwayomi-Server/server/src/main/kotlin/suwayomi/tachidesk/graphql/controller/GraphQLController.kt) serves as the main entry point for handling GraphQL queries, mutations, and subscriptions.
    *   **`execute(ctx: Context)`**: Handles GraphQL query execution asynchronously.
    *   **`playground(ctx: Context)`**: Serves the GraphQL Playground HTML interface.
    *   **`retrieveFile(ctx: Context)`**: Manages retrieval of temporary files.
    *   **`webSocket(ws: WsConfig)`**: Configures WebSocket for GraphQL subscriptions.
*   **Dependencies**: The server-side dependencies are not explicitly listed in a `package.json` equivalent for Kotlin/Gradle projects in the initial file list. A more in-depth analysis of `build.gradle` files would be required to list them.

### Suwayomi-WebUI Analysis
*   **Documentation**:
    *   **[`Suwayomi-WebUI/README.md`](Suwayomi-WebUI/README.md)**: Provides a high-level overview of features (Library management, Reader, Download queue, Reading history, Settings, Sources, App updates, Themes), preview versions, and contribution/translation information.
    *   **[`Suwayomi-WebUI/BUILDING.md`](Suwayomi-WebUI/BUILDING.md)**: Details build instructions using [Vite](https://vitejs.dev/) and `yarn` scripts (`dev`, `preview`, `build`).
    *   **[`Suwayomi-WebUI/CONTRIBUTING.md`](Suwayomi-WebUI/CONTRIBUTING.md)**: Outlines contribution guidelines, including "WebUI to Server mapping" for version compatibility and coding style (e.g., no relative imports, use MUI v5 `sx` or `styled` for styling).
*   **Type Definitions and Interfaces**:
    *   **GraphQL Generated Types**: [`Suwayomi-WebUI/src/lib/graphql/generated/graphql.ts`](Suwayomi-WebUI/src/lib/graphql/generated/graphql.ts) defines the GraphQL schema types used for communication with the server.
    *   **Reader-specific Types**: The `Suwayomi-WebUI/src/modules/reader/types/` directory contains specific type definitions for the reader component:
        *   [`Suwayomi-WebUI/src/modules/reader/types/Reader.types.ts`](Suwayomi-WebUI/src/modules/reader/types/Reader.types.ts)
        *   [`Suwayomi-WebUI/src/modules/reader/types/ReaderOverlay.types.ts`](Suwayomi-WebUI/src/modules/reader/types/ReaderOverlay.types.ts)
        *   [`Suwayomi-WebUI/src/modules/reader/types/ReaderProgressBar.types.ts`](Suwayomi-WebUI/src/modules/reader/types/ReaderProgressBar.types.ts)
        *   [`Suwayomi-WebUI/src/modules/reader/types/TapZoneLayout.types.ts`](Suwayomi-WebUI/src/modules/reader/types/TapZoneLayout.types.ts)
*   **Implementations**: The WebUI is a React application structured into modules under `src/modules/`. Key areas include:
    *   **Main Entry**: `index.html`.
    *   **Module Structure**: `src/modules/` contains feature-specific modules (e.g., `app-updates`, `backup`, `browse`, `reader`).
    *   **GraphQL Integration**: `src/lib/graphql/` handles client-side GraphQL operations (fragments, mutations, queries, subscriptions).
    *   **Core UI Components**: `src/modules/core/components/` houses reusable UI elements.
    *   **Reader Logic**: `src/modules/reader/` contains the core logic, UI, hooks, and services for the manga reader.
*   **Critical Dependencies**: The `Suwayomi-WebUI/package.json` file lists numerous dependencies, including:
    *   **Build Tool**: Vite.
    *   **Frontend Framework**: React, React DOM.
    *   **Styling**: `@emotion/react`, `@emotion/styled`, `@mui/material` (MUI v5), `stylis`, `stylis-plugin-rtl`.
    *   **GraphQL Client**: `@apollo/client`, `apollo-upload-client`, `graphql`, `graphql-tag`, `graphql-ws`, `graphql-sock`.
    *   **Routing**: `react-router-dom`.
    *   **Internationalization**: `i18next`, `react-i18next`, `i18next-browser-languagedetector`, `i18next-http-backend`.
    *   **Date Management**: `dayjs`.
    *   **Drag and Drop**: `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`.
    *   **Virtualization**: `react-virtuoso`.
    *   **Utility Libraries**: `p-limit`, `jsonrepair`, `sanitize-html`, `fast-average-color`, `node-vibrant`.
    *   **Development Dependencies**: `typescript`, `eslint`, `prettier`, `husky`, `lint-staged`, `@graphql-codegen/cli`, `tsx`.

## Challenges Encountered
*   Initial attempts to read server-side Kotlin files and specific WebUI type definition files failed due to incorrect paths. This was resolved by recursively listing directories and then identifying the correct file locations.