echo "[`date`] Welcome to Timbuctoo :)" | tee /log/timbuctoo.log
./bin/timbuctoo server ./example_config.yaml 2>&1 | tee -a /log/timbuctoo.log
