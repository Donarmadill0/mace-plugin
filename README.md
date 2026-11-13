# Plutus Mace Plugin (1.21.1)

A custom Paper plugin that adds the **Plutus Mace**, a cursed weapon with crimson effects and blood-based abilities.

## Features
- **Red Themed Visuals**: Custom particles (crimson spores, smoke, red dust).
- **Custom Texture**: Red-headed spiked mace (via included resource pack assets).
- **Blood Toll (Passive)**: Taking damage while attacking (configurable).
- **Blood Dash (Ability 1)**: Dash forward with a burst of red particles, damaging nearby enemies. (Press **F** or Swap Hand).
- **Blood Pact (Ability 2)**: Link up to 3 players. Linked players share damage taken. (Press **Shift + F**).

## How to Build (Get the .jar)

Since I cannot build the JAR for you on this system due to security restrictions, follow these steps:

1. **Install Java 21**: Ensure you have JDK 21 installed on your computer.
2. **Move the files**: Copy the `plutus-mace` folder to a place where you have write access.
3. **Run Gradle**: Open a terminal/command prompt in the `plutus-mace` folder and run:
   ```bash
   ./gradlew build
   ```
   (If you don't have the gradle wrapper, you can just open this folder as a project in **IntelliJ IDEA** and click Build).
4. **Find the JAR**: The finished plugin will be at `build/libs/plutus-mace-1.0.0.jar`.

## How to use the Texture

The plugin includes the texture files in `src/main/resources/assets`. To see the red mace in-game:
1. Zip the `assets` folder (found in `src/main/resources/`) along with a `pack.mcmeta` file.
2. Load it as a **Resource Pack** in Minecraft.
3. Or, use a plugin like `ItemsAdder` or `Oraxen` to host the assets.

## Commands
- `/giveplutus`: Gives the Plutus Mace to the player (Permission: `plutus.admin`).

## Configuration
Edit `config.yml` to change damage values, cooldowns, and particle intensity.
