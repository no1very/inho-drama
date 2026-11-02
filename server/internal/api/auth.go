package api

import (
	"errors"
	"net/http"

	"nasdrama/server/internal/store"
)

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleServerInfo(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, ServerInfoDTO{
		Name:          "InhoDrama",
		Version:       s.opts.Version,
		AllowRegister: s.opts.AllowRegister,
	})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if err := readJSON(w, r, &req); err != nil {
		writeErr(w, http.StatusBadRequest, "请求体格式错误")
		return
	}
	if req.Username == "" || req.Password == "" {
		writeErr(w, http.StatusBadRequest, "用户名和密码不能为空")
		return
	}
	u, err := s.opts.Store.VerifyLogin(req.Username, req.Password)
	if err != nil {
		writeErr(w, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	tok, err := s.opts.Store.CreateToken(u.ID, s.opts.TokenTTL)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "创建会话失败")
		return
	}
	writeJSON(w, http.StatusOK, AuthResponse{Token: tok, User: UserDTO{ID: u.ID, Username: u.Username}})
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	if !s.opts.AllowRegister {
		writeErr(w, http.StatusForbidden, "注册已关闭")
		return
	}
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if err := readJSON(w, r, &req); err != nil {
		writeErr(w, http.StatusBadRequest, "请求体格式错误")
		return
	}
	if req.Username == "" || len(req.Password) < 6 {
		writeErr(w, http.StatusBadRequest, "用户名不能为空，密码至少 6 位")
		return
	}
	u, err := s.opts.Store.CreateUser(req.Username, req.Password)
	if errors.Is(err, store.ErrConflict) {
		writeErr(w, http.StatusConflict, "用户名已存在")
		return
	}
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "创建用户失败")
		return
	}
	tok, err := s.opts.Store.CreateToken(u.ID, s.opts.TokenTTL)
	if err != nil {
		writeErr(w, http.StatusInternalServerError, "创建会话失败")
		return
	}
	writeJSON(w, http.StatusOK, AuthResponse{Token: tok, User: UserDTO{ID: u.ID, Username: u.Username}})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request, u *store.User) {
	writeJSON(w, http.StatusOK, map[string]UserDTO{"user": {ID: u.ID, Username: u.Username}})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request, u *store.User) {
	if tok := bearerToken(r); tok != "" {
		_ = s.opts.Store.DeleteToken(tok)
	}
	writeOK(w)
}
