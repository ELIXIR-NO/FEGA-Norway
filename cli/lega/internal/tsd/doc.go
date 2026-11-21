// Package tsd is the client for the TSD file API, used only by upload
// --direct. Chunks and the final chunk=end go straight to TSD because the
// TSD token is bound to the client's IP and TSD's nginx restricts writes on
// /ega/ by IP (see FEGA-Norway#776).
package tsd
