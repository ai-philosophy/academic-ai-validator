const API_BASE = import.meta.env.VITE_API_BASE || "";

export async function sendAcademicChat(payload) {
  const response = await fetch(`${API_BASE}/api/academic-chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || "Không thể kiểm định nội dung.");
  }

  return data;
}
