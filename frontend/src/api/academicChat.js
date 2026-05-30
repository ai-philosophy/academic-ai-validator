const API_BASE = import.meta.env.VITE_API_BASE || "";

function buildErrorMessage(response, data) {
  const detail = data?.message || data?.error;
  if (detail) {
    return `Không thể kiểm định nội dung: ${detail}`;
  }

  if (response.status >= 500) {
    return `Không thể kiểm định nội dung (HTTP ${response.status}). Kiểm tra backend port 8080 hoặc cấu hình GitHub Models.`;
  }

  return `Không thể kiểm định nội dung (HTTP ${response.status}).`;
}

export async function sendAcademicChat(payload) {
  let response;
  try {
    response = await fetch(`${API_BASE}/api/academic-chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });
  } catch {
    throw new Error("Không kết nối được backend. Kiểm tra BE đang chạy ở port 8080 và FE proxy đúng cấu hình.");
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(buildErrorMessage(response, data));
  }

  return data;
}
