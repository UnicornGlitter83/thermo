

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <curl/curl.h>



#define TEMP_FILE "/tmp/temp"
#define STATUS_FILE "/tmp/status"
#define SERVER_URL "http://18.116.46.202:8001/"


int read_file(const char *filename, char *buffer, size_t size) {
	FILE *fp = fopen(filename, "r");
	if (!fp) return -1;
	if (!fgets(buffer, size, fp)) {
		fclose(fp);
		return -2;
	}

	buffer[strcspn(buffer, "\n")] = 0;
	fclose(fp);
	return 0;
}

int main(void) {
	CURL *curl;
	CURLcode res;
	char temp_str[32];
	char status_str[32];
	char json_payload[128];

	curl_global_init(CURL_GLOBAL_DEFAULT);
	curl = curl_easy_init();
	if(!curl) {
		fprintf(stderr, "Curl failed to initialize\n");
		return 1;
	}

	while(1) {
		if(read_file(TEMP_FILE, temp_str, sizeof(temp_str)) != 0) {
			fprintf(stderr, "Temperature read failed\n");
			sleep(6);
			continue;
		}

		if(read_file(STATUS_FILE, status_str, sizeof(status_str)) != 0) {
			fprintf(stderr, "Status read failed\n");
			sleep(6);
			continue;
		}

		snprintf(json_payload, sizeof(json_payload), "{\"temp\":%s, \"status\":\"%s\"}", temp_str, status_str);

		curl_easy_setopt(curl, CURLOPT_URL, SERVER_URL);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_payload);


		struct curl_slist *headers = NULL;
		headers = curl_slist_append(headers, "Content-Type: application/json");
		curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);


		res = curl_easy_perform(curl);
		if(res != CURLE_OK)
			fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
		else
			printf("Posted: %s\n", json_payload);

		curl_slist_free_all(headers);

		sleep(6);

	}

	curl_easy_cleanup(curl);
	curl_global_cleanup();

	return 0;
}


