package main

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestLoadUsers(t *testing.T) {
	loadUsers()
	if len(users) == 0 {
		t.Fatal("Expected users to be loaded")
	}
	if len(usernames) == 0 || len(uids) == 0 {
		t.Fatal("Expected username and uid maps to be populated")
	}
}

func newAuthorizedRequest(method, path, username, password string) *http.Request {
	req, _ := http.NewRequest(method, path, nil)
	auth := base64.StdEncoding.EncodeToString([]byte(username + ":" + password))
	req.Header.Set("Authorization", "Basic "+auth)
	return req
}

func TestUserHandler_ValidUsername(t *testing.T) {
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

func TestUserHandler_InvalidUser(t *testing.T) {
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

func TestUserHandler_Unauthorized(t *testing.T) {
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

func TestUserHandler_ValidUID(t *testing.T) {
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
