;; Calls through the owner's table; its global, memory and type indices differ from the owner's.
(module
  (type $byIndex (func (param i32) (result i32)))
  (type $i64 (func (result i64)))
  (type $pair (func (result i32 i32)))
  (type $i32 (func (result i32)))
  (import "owner" "tab" (table 6 funcref))
  (memory 1)
  (global $g (mut i32) (i32.const -1))

  (func (export "call") (type $byIndex)
    (call_indirect (type $i32) (local.get 0)))

  (func (export "tailCall") (type $byIndex)
    (return_call_indirect (type $i32) (local.get 0)))

  (func (export "callPair") (result i32)
    (call_indirect (type $pair) (i32.const 4))
    i32.sub)

  (func (export "wrongType") (result i64)
    (call_indirect (type $i64) (i32.const 0)))
)
