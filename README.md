# ResQMesh 🆘
### Offline Emergency BLE Mesh Network

[![Android](https://img.shields.io/badge/Platform-Android-green)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue)](https://kotlinlang.org)
[![BLE](https://img.shields.io/badge/Tech-Bluetooth_LE-purple)](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
[![SIH 2026](https://img.shields.io/badge/Event-SIH_2026-orange)](https://www.sih.gov.in)

---

## 📌 Problem Statement
**SIH Problem ID:** SIH26206  
**Theme:** Disaster Management  
**Event:** Smart India Hackathon 2026

During disasters, cellular networks collapse — leaving survivors with no way to call for help. ResQMesh solves this by creating a peer-to-peer Bluetooth mesh that works **completely offline**, with **no internet, no cell towers, no infrastructure required**.

---

## 🚀 How It Works

```
[Survivor] → BLE → [Bystander] → BLE → [Bystander] → BLE → [Rescue Team]
  hop 0              hop 1               hop 2               GATEWAY (sinks SOS)
```

1. **Survivor** taps SOS — packet broadcasts over Bluetooth LE (hop 0)
2. **Nearby phones** with ResQMesh automatically detect and re-relay the packet (hop 1, 2, 3...)
3. **Rescue Team** phone (Gateway) receives the SOS with GPS coordinates, displays on dashboard
4. **ACK** travels back through the mesh — survivor sees "Delivered ✅"

Each BLE hop covers ~50m. With 20 relay phones = **1 km range**. With 50 phones = **2.5 km**.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔴 SOS Button | One-tap emergency broadcast with GPS coordinates |
| 📡 BLE Mesh Relay | Automatic multi-hop packet forwarding through bystanders |
| 🛡️ Rescue Dashboard | Live feed of all incoming SOS signals with priority triage |
| 🗺️ GPS Location | Survivor coordinates embedded in every SOS packet |
| ✅ Delivery ACK | Confirmation when SOS reaches rescue team |
| 🔁 50-Hop Mesh | Supports relay chains up to ~2.5 km |
| 📍 Offline Maps | Opens survivor location in device's offline map app |
| 🔒 AES-GCM Encryption | Binary packet protocol with encrypted payload (ResQPacket) |
| 🎯 Smart Routing | Gradient routing — packets prefer paths closer to gateway |

---

## 📱 Node Roles

| Role | Who | Behavior |
|---|---|---|
| **SURVIVOR** | Person in distress | Originates SOS, sends GPS |
| **RELAY** | Bystander phone | Auto-relays packets toward gateway |
| **GATEWAY** | Rescue team phone | Receives SOS, shows on dashboard, sends ACK |

The **Vivo I2403** device auto-starts as GATEWAY. All other devices start as SURVIVOR/RELAY.

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **BLE:** Android BluetoothLE (GATT Server/Client)
- **Location:** Google Play Services FusedLocationProvider
- **Navigation:** Jetpack Navigation Compose
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Packet Protocol:** Custom binary protocol with AES-128-GCM encryption

---

## 📂 Project Structure

```
app/src/main/java/com/resqmesh/app/
├── HomeActivity.kt          # Navigation host, role selection
├── SurvivorActivity.kt      # SOS screen for survivors
├── RescueDashboardActivity.kt # Live SOS feed for rescue team
├── BleManager.kt            # BLE mesh transport (scan + advertise + relay)
├── RoutingManager.kt        # Gradient routing logic
├── MeshViewModel.kt         # MVVM state management
├── MeshMessage.kt           # Canonical SOS data model
├── MessageStore.kt          # In-memory message store
├── ResQPacket.kt            # Binary packet protocol + AES-GCM
├── ResQLocationManager.kt   # GPS wrapper
├── DeviceConfig.kt          # Gateway device detection
├── MessageDetailScreen.kt   # SOS detail view
├── OfflineMapScreen.kt      # Offline map integration
├── PermissionsScreen.kt     # Runtime permissions
└── AboutScreen.kt           # App info + team details
```

---

## 🏃 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android phone with BLE support (API 24+)
- For testing mesh: minimum 3 phones

### Build & Run
```bash
git clone https://github.com/deekshitha875/ResQMesh.git
cd ResQMesh
# Open in Android Studio → Run
```

### Permissions Required
- `BLUETOOTH_SCAN` — discover nearby relay nodes
- `BLUETOOTH_ADVERTISE` — broadcast as relay
- `BLUETOOTH_CONNECT` — connect to peer devices
- `ACCESS_FINE_LOCATION` — GPS for SOS coordinates + BLE scanning

---

## 🧪 Testing the Mesh

| Phone | Action | Role |
|---|---|---|
| Phone 1 | Tap "RESCUE TEAM" | Gateway |
| Phone 2 | Tap "I NEED HELP" | Survivor |
| Phone 3 (middle) | Open app, put in pocket | Relay |

Place Phone 1 and Phone 2 more than 15m apart. Phone 3 in between. Tap SOS on Phone 2 — it should appear on Phone 1's dashboard with `hopCount: 1`.

---

## 👥 Team

| | |
|---|---|
| **Team Name** | OG |
| **Team ID** | TEAM-145 |
| **Hackathon** | Smart India Hackathon 2026 |

---

## 📄 License

This project was built for Smart India Hackathon 2026. All rights reserved © OG 2026.
