import crypto from "node:crypto";

function safeEqual(a, b) {
  const left = Buffer.from(a ?? "");
  const right = Buffer.from(b ?? "");
  if (left.length !== right.length) return false;
  return crypto.timingSafeEqual(left, right);
}

export function bearerAuth(expectedToken) {
  return (req, res, next) => {
    const header = req.get("authorization") ?? "";
    const provided = header.startsWith("Bearer ") ? header.slice(7) : "";
    if (!safeEqual(provided, expectedToken)) {
      return res.status(401).json({ error: "Unauthorized" });
    }
    next();
  };
}

