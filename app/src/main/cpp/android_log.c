/* Sends stdout/stderr to a text file, because a phone has no easy logcat.
 * Look for /storage/emulated/0/Quake/native_log.txt after a run. */
#include <stdio.h>
#include <sys/stat.h>

#define QUAKE_DIR "/storage/emulated/0/Quake"
#define LOG_FILE  QUAKE_DIR "/native_log.txt"

__attribute__((constructor))
static void redirect_output_to_file(void)
{
    struct stat st;
    if (stat(QUAKE_DIR, &st) != 0)
        return;
    remove(LOG_FILE);
    if (freopen(LOG_FILE, "a", stdout))
        setvbuf(stdout, NULL, _IOLBF, 0);
    freopen(LOG_FILE, "a", stderr);
}
