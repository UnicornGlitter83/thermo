


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>

#define TEMP_FILE "/tmp/temp"
#define STATUS_FILE "/tmp/status"
#define SETPOINT 72.0
#define HYSTERESIS 3.0

int file_exists_and_not_empty(const char *filename) {
	struct stat st;
	if (stat(filename, &st) == 0 && st.st_size > 0) {
		return 1;
	}
	return 0;
}


int main() {

	if (!file_exists_and_not_empty(STATUS_FILE)) {
		FILE *sf = fopen(STATUS_FILE, "w");
		if (sf == NULL) {
			perror("Failed to initialize status file");
			return 1;
		}
		fprintf(sf, "OFF\n");
		fclose(sf);
	}

	float temp;
	char status[8] = "OFF";

	while (1) {
		FILE *tf = fopen(TEMP_FILE, "r");
		if (tf == NULL) {
			perror("Cannot read temp");
			sleep(5);
			continue;
		}
		fscanf(tf, "%f", &temp);
		fclose(tf);

		FILE *sf = fopen(STATUS_FILE, "r");
		if (sf != NULL) {
			fgets(status, sizeof(status), sf);
			fclose(sf);
		}


		if (temp < (SETPOINT - HYSTERESIS) && strncmp(status, "ON", 2) != 0) {
			sf = fopen(STATUS_FILE, "w");
			if (sf != NULL) {
				fprintf(sf, "ON\n");
				fclose(sf);
			}
		} else if (temp > (SETPOINT + HYSTERESIS) && strncmp(status, "OFF", 3) != 0) {
			sf = fopen(STATUS_FILE, "w");
                        if (sf != NULL) {
				fprintf(sf, "OFF\n");
        	                fclose(sf);
			}
		}

		sleep(5);
	}

	return 0;
}


