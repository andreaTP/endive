;; Has the owner grow the shared memory, then stores into the new page.
(module
  (type $i32 (func (result i32)))
  (import "owner" "mem" (memory 1))
  (import "owner" "tab" (table 1 funcref))

  (func (export "growThenStore") (result i32)
    (drop (call_indirect (type $i32) (i32.const 0)))
    (i32.store (i32.const 65536) (memory.size))
    (i32.load (i32.const 65536)))
)
