package dev.fl3xy.wallhue;

interface IOverlayService {
    void destroy() = 16777114;
    // Returns null on success, otherwise the error text.
    String grant(String packageName) = 1;
}
