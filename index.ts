import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

serve(async (req) => {
  const auth = req.headers.get("Authorization") ?? "";
  if (auth !== `Bearer ${Deno.env.get("ADMIN_API_SECRET")}`) {
    return new Response(JSON.stringify({ error_code: "UNAUTHORIZED" }), { status: 401 });
  }

  const body = await req.json();
  const licenseKey = String(body.license_key ?? "").trim().toUpperCase();
  const action = String(body.action ?? "REVOKE").toUpperCase(); // TRANSFER_RESET or REVOKE
  const reason = String(body.reason ?? "");

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );

  const { data: lic } = await supabase
    .from("licenses")
    .select("id")
    .eq("license_key", licenseKey)
    .maybeSingle();

  if (!lic) {
    return new Response(JSON.stringify({ error_code: "KEY_NOT_FOUND" }), { status: 404 });
  }

  await supabase
    .from("device_activations")
    .update({ is_active: false })
    .eq("license_id", lic.id)
    .eq("is_active", true);

  const nextStatus = action === "TRANSFER_RESET" ? "TRANSFERRED" : "REVOKED";
  await supabase.from("licenses").update({ status: nextStatus }).eq("id", lic.id);

  await supabase.from("license_audit_logs").insert({
    license_id: lic.id,
    event_type: action === "TRANSFER_RESET" ? "DEVICE_TRANSFER" : "KEY_REVOKED",
    metadata_json: { reason },
  });

  return new Response(
    JSON.stringify({ status: "updated", license_status: nextStatus, active_devices: 0 }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
});
