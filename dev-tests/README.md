Scripted tests for the combat logic (uppercut order, KB displacement ticks, taps, retreat time, reach spacing, crit timing,
spear lunge, mace chain, shield-when-above). `./run.sh` compiles the real controller sources against stand-ins for the Minecraft
types and drives them tick by tick. It checks the decision logic only; it cannot check anything that needs the game itself.
