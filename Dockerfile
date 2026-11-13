FROM eclipse-temurin:21-jre
# Keep large native image allocations reclaimable and limit allocator arenas.
ENV MALLOC_ARENA_MAX=2
ENV GLIBC_TUNABLES=glibc.malloc.mmap_threshold=131072
WORKDIR /app
COPY build/libs/app.jar app.jar
RUN mkdir -p /app/profile-images && chown 1000:1000 /app/profile-images
USER 1000:1000
EXPOSE 8080
# Use the first JIT tier to leave memory for native work in the API container.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:TieredStopAtLevel=1", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
