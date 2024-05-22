package main

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
)

// TestLoadUsers tests the loadUsers function
func TestLoadUsers(t *testing.T) {
	loadUsers()
	if len(users) == 0 {
		t.Fatal("Expected users to be loaded")
	}
	if len(usernames) == 0 || len(uids) == 0 {
		t.Fatal("Expected username and uid maps to be populated")
	}
}

// Helper function to create a new request with authorization
func newAuthorizedRequest(method, path, username, password string) *http.Request {
	req, _ := http.NewRequest(method, path, nil)
	auth := base64.StdEncoding.EncodeToString([]byte(username + ":" + password))
	req.Header.Set("Authorization", "Basic "+auth)
	return req
}

// TestUserHandler_ValidUsername tests the userHandler function with a valid username request
func TestUserHandler_ValidUsername(t *testing.T) {
	// Setup
	loadUsers()
	instances = map[string]string{
		"test_instance": "test_password",
	}

	req := newAuthorizedRequest("GET", "/lega/v1/legas/users/dummy?idType=username", "test_instance", "test_password")
	rr := httptest.NewRecorder()
	handler := http.HandlerFunc(userHandler)
	handler.ServeHTTP(rr, req)

	if status := rr.Code; status != http.StatusOK {
		t.Errorf("handler returned wrong status code: got %v want %v", status, http.StatusOK)
	}

	var response map[string]interface{}
	if err := json.Unmarshal(rr.Body.Bytes(), &response); err != nil {
		t.Fatalf("Failed to parse response: %v", err)
	}

	if response["response"].(map[string]interface{})["numTotalResults"].(float64) != 1 {
		t.Errorf("Expected one user in response")
	}
}

// TestUserHandler_InvalidUser tests the userHandler function with an invalid user request
func TestUserHandler_InvalidUser(t *testing.T) {
	// Setup
	loadUsers()
	instances = map[string]string{
		"test_instance": "test_password",
	}

	req := newAuthorizedRequest("GET", "/lega/v1/legas/users/invalid_user?idType=username", "test_instance", "test_password")
	rr := httptest.NewRecorder()
	handler := http.HandlerFunc(userHandler)
	handler.ServeHTTP(rr, req)

	if status := rr.Code; status != http.StatusBadRequest {
		t.Errorf("handler returned wrong status code: got %v want %v", status, http.StatusBadRequest)
	}
}

// TestUserHandler_Unauthorized tests the userHandler function with an unauthorized request
func TestUserHandler_Unauthorized(t *testing.T) {
	// Setup
	loadUsers()
	instances = map[string]string{
		"test_instance": "test_password",
	}

	req := newAuthorizedRequest("GET", "/lega/v1/legas/users/dummy?idType=username", "wrong_instance", "wrong_password")
	rr := httptest.NewRecorder()
	handler := http.HandlerFunc(userHandler)
	handler.ServeHTTP(rr, req)

	if status := rr.Code; status != http.StatusUnauthorized {
		t.Errorf("handler returned wrong status code: got %v want %v", status, http.StatusUnauthorized)
	}
}

// TestUserHandler_ValidUID tests the userHandler function with a valid UID request
func TestUserHandler_ValidUID(t *testing.T) {
	// Setup
	loadUsers()
	instances = map[string]string{
		"test_instance": "test_password",
	}

	req := newAuthorizedRequest("GET", "/lega/v1/legas/users/1?idType=uid", "test_instance", "test_password")
	rr := httptest.NewRecorder()
	handler := http.HandlerFunc(userHandler)
	handler.ServeHTTP(rr, req)

	if status := rr.Code; status != http.StatusOK {
		t.Errorf("handler returned wrong status code: got %v want %v", status, http.StatusOK)
	}
}

// Main function to set environment variables and run tests
func TestMain(m *testing.M) {
	os.Setenv("CEGA_USERS_USER", "test_instance")
	os.Setenv("CEGA_USERS_PASSWORD", "test_password")
	loadUsers()
	code := m.Run()
	os.Exit(code)
}
